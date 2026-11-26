package io.github.mario2712.locus.ui.review

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.geo.CountryShape
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import io.github.mario2712.locus.domain.reference.ReferenceData
import io.github.mario2712.locus.domain.stats.YearReviewData
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.common.Format
import io.github.mario2712.locus.ui.trips.tripTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- Gestaltung: Reiseatlas, ruhig und elegant

private val Serif = FontFamily(Font(R.font.instrument_serif))

private data class Atlas(
    val paper: Color, val sheet: Color, val ink: Color, val inkSoft: Color, val rule: Color,
    val sea: Color, val land: Color, val visited: Color, val route: Color,
    val alltag: Color, val zuhause: Color, val urlaub: Color, val ausflug: Color, val dienst: Color, val unterwegs: Color,
    val arbeit: Color, val ausbildung: Color,
)

private val LightAtlas = Atlas(
    paper = Color(0xFFEBEFEC), sheet = Color(0xFFF6F8F5), ink = Color(0xFF1E2A33), inkSoft = Color(0xFF5B6A72), rule = Color(0xFFC9D2CD),
    sea = Color(0xFFDCE5E3), land = Color(0xFFCED8D2), visited = Color(0xFF2E6E68), route = Color(0xFFA8742F),
    alltag = Color(0xFFC3CCC6), zuhause = Color(0xFF8FA39A), urlaub = Color(0xFF2E6E68), ausflug = Color(0xFFC79A4E),
    dienst = Color(0xFF50708E), unterwegs = Color(0xFFB7A890), arbeit = Color(0xFF8A9BAE), ausbildung = Color(0xFF9C7A52),
)

private val DarkAtlas = Atlas(
    paper = Color(0xFF11171C), sheet = Color(0xFF172028), ink = Color(0xFFE3E8E4), inkSoft = Color(0xFF97A6A4), rule = Color(0xFF2A363E),
    sea = Color(0xFF142027), land = Color(0xFF26333A), visited = Color(0xFF5BA59C), route = Color(0xFFD9A45A),
    alltag = Color(0xFF2C3940), zuhause = Color(0xFF5E7A70), urlaub = Color(0xFF5BA59C), ausflug = Color(0xFFD2A85E),
    dienst = Color(0xFF7C9BBA), unterwegs = Color(0xFF8E836F), arbeit = Color(0xFF55667A), ausbildung = Color(0xFFB08C62),
)

/** Tagesart für die Heatmap (Farbe + Legende). */
private enum class DayKind { ALLTAG, ZUHAUSE, AUSFLUG, URLAUB, DIENST, UNTERWEGS, ARBEIT, AUSBILDUNG, LEER }

private fun Atlas.color(kind: DayKind): Color = when (kind) {
    DayKind.ALLTAG -> alltag; DayKind.ZUHAUSE -> zuhause; DayKind.AUSFLUG -> ausflug; DayKind.URLAUB -> urlaub
    DayKind.DIENST -> dienst; DayKind.UNTERWEGS -> unterwegs; DayKind.ARBEIT -> arbeit; DayKind.AUSBILDUNG -> ausbildung
    DayKind.LEER -> Color.Transparent
}

@Composable
private fun kindLabel(kind: DayKind, includeWork: Boolean): String = stringResource(
    when (kind) {
        DayKind.ALLTAG -> if (includeWork) R.string.review_kind_none else R.string.review_kind_everyday
        DayKind.ZUHAUSE -> R.string.review_kind_home
        DayKind.AUSFLUG -> R.string.review_kind_excursion
        DayKind.URLAUB -> R.string.review_kind_vacation
        DayKind.DIENST -> R.string.review_kind_business
        DayKind.UNTERWEGS -> R.string.review_kind_travel
        DayKind.ARBEIT -> R.string.review_kind_work
        DayKind.AUSBILDUNG -> R.string.review_kind_education
        DayKind.LEER -> R.string.review_kind_none
    },
)

private fun dayKind(typeId: String?, editor: EditorData): DayKind {
    typeId ?: return DayKind.ALLTAG
    return when {
        typeId == StayTypeIds.EXCURSION -> DayKind.AUSFLUG
        typeId == StayTypeIds.BUSINESS_TRIP -> DayKind.DIENST
        typeId == StayTypeIds.TRANSIT || typeId == StayTypeIds.ON_THE_WAY -> DayKind.UNTERWEGS
        else -> when (editor.typesById[typeId]?.group) {
            StayGroup.VACATION -> DayKind.URLAUB
            StayGroup.WORK -> DayKind.ARBEIT
            StayGroup.EDUCATION -> DayKind.AUSBILDUNG
            else -> DayKind.ZUHAUSE
        }
    }
}

private fun categoryGlyph(categoryId: String?): String = when (categoryId) {
    VisitCategoryIds.RESTAURANT -> "🍽️"; VisitCategoryIds.BAR -> "🍸"; VisitCategoryIds.SIGHT -> "🏛️"
    VisitCategoryIds.NATURE, VisitCategoryIds.SUMMIT -> "🥾"; VisitCategoryIds.MUSEUM -> "🖼️"; VisitCategoryIds.EVENT -> "🎵"
    VisitCategoryIds.ACTIVITY -> "🎟️"; VisitCategoryIds.SHOPPING -> "🛒"; VisitCategoryIds.SPORT -> "🏃"
    VisitCategoryIds.HEALTH -> "⚕️"; VisitCategoryIds.ACCOMMODATION -> "🛏️"; VisitCategoryIds.REST_STOP -> "⛽"
    else -> "·"
}

private fun km(meters: Double) = String.format(Locale.GERMAN, "%,.0f", meters / 1000)

// ---------------------------------------------------------------- Bildschirm

/**
 * Jahresrückblick als Story im Stil eines Reiseatlas: sechs Seiten, wischen oder tippen (rechts weiter, links zurück).
 * Oben „Teilen“ (aktuelle Seite) und „Alles“ (alle Seiten als ein langes Bild).
 */
@Composable
fun YearReviewScreen(onClose: () -> Unit, viewModel: YearReviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data = state.data
    val atlas = if (isSystemInDarkTheme()) DarkAtlas else LightAtlas
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layers = remember { mutableStateMapOf<Int, GraphicsLayer>() }
    var capturing by remember { mutableStateOf(false) }
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    Box(Modifier.fillMaxSize().background(atlas.paper)) {
        if (data == null) {
            Text(stringResource(R.string.search_loading), color = atlas.inkSoft, modifier = Modifier.align(Alignment.Center))
            return@Box
        }
        val pages = buildList<@Composable ColumnScope.(Boolean) -> Unit> {
            add { active -> TitlePage(state, data, atlas, active, reducedMotion || capturing) }
            add { active -> TripsPage(state, data, atlas, active, reducedMotion || capturing) }
            add { active -> ColorsPage(state, data, atlas, active, reducedMotion || capturing) }
            add { active -> PlacesPage(state, data, atlas, active, reducedMotion || capturing) }
            add { _ -> MomentsPage(state, data, atlas) }
            if (data.previous != null || data.togetherDays > 0) add { active -> TogetherPage(state, data, atlas, active, reducedMotion || capturing) }
        }
        val pager = rememberPagerState { pages.size }

        HorizontalPager(
            pager,
            Modifier.fillMaxSize().pointerInput(pages.size) {
                detectTapGestures { offset ->
                    scope.launch {
                        val target = if (offset.x > size.width * 0.38f) pager.currentPage + 1 else pager.currentPage - 1
                        if (target in 0 until pages.size) pager.animateScrollToPage(target)
                    }
                }
            },
        ) { index ->
            val layer = rememberGraphicsLayer()
            DisposableEffect(index, layer) {
                layers[index] = layer
                onDispose { if (layers[index] === layer) layers.remove(index) }
            }
            Column(
                Modifier.fillMaxSize()
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(atlas.sheet)
                    .systemBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                pages[index](pager.currentPage == index)
            }
        }

        // Fortschritt, Schließen, Teilen (nicht Teil der geteilten Bilder)
        Column(Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(pages.size) { i ->
                    Box(Modifier.weight(1f).height(2.dp).background(if (i <= pager.currentPage) atlas.ink else atlas.rule, RoundedCornerShape(1.dp)))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("✕", color = atlas.ink, fontSize = 18.sp) }
                Text("Locus · ${data.year}", color = atlas.inkSoft, style = monoStyle(), modifier = Modifier.weight(1f))
                // Arbeit ein-/ausblenden (Standard: aus; Dienstreisen zählen immer als Reisen)
                TextButton(enabled = !capturing, onClick = { viewModel.setIncludeWork(!state.includeWork) }) {
                    Text(
                        stringResource(if (state.includeWork) R.string.review_work_on else R.string.review_work_off),
                        color = if (state.includeWork) atlas.visited else atlas.inkSoft,
                    )
                }
                TextButton(enabled = !capturing, onClick = {
                    val layer = layers[pager.currentPage] ?: return@TextButton
                    scope.launch {
                        shareBitmap(context, layer.toImageBitmap().asAndroidBitmap(), "locus-rueckblick-${data.year}-${pager.currentPage + 1}.png")
                    }
                }) { Text(stringResource(R.string.review_share), color = atlas.ink) }
                TextButton(enabled = !capturing, onClick = {
                    scope.launch {
                        val back = pager.currentPage
                        capturing = true
                        val shots = mutableListOf<Bitmap>()
                        for (i in pages.indices) {
                            pager.scrollToPage(i)
                            withFrameNanos { }
                            withFrameNanos { }
                            delay(60)
                            layers[i]?.let { shots += it.toImageBitmap().asAndroidBitmap() }
                        }
                        pager.scrollToPage(back)
                        capturing = false
                        if (shots.isNotEmpty()) shareBitmap(context, stitch(shots, atlas.rule), "locus-rueckblick-${data.year}.jpg")
                    }
                }) { Text(stringResource(R.string.review_share_all), color = atlas.ink) }
            }
        }
    }
}

/** Seiten untereinander zu einem langen Bild (verkleinert auf 720 px Breite, damit es teilbar bleibt). */
private fun stitch(shots: List<Bitmap>, rule: Color): Bitmap {
    val width = 720
    val scaled = shots.map { b -> Bitmap.createScaledBitmap(b.copy(Bitmap.Config.ARGB_8888, false), width, (b.height * width.toFloat() / b.width).roundToInt(), true) }
    val gap = 2
    val out = Bitmap.createBitmap(width, scaled.sumOf { it.height } + gap * (scaled.size - 1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawColor(android.graphics.Color.argb((rule.alpha * 255).toInt(), (rule.red * 255).toInt(), (rule.green * 255).toInt(), (rule.blue * 255).toInt()))
    var y = 0f
    scaled.forEach { canvas.drawBitmap(it, 0f, y, null); y += it.height + gap }
    return out
}

private fun shareBitmap(context: Context, bitmap: Bitmap, name: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    val file = File(dir, name)
    file.outputStream().use {
        if (name.endsWith(".jpg")) bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) else bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = if (name.endsWith(".jpg")) "image/jpeg" else "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

// ---------------------------------------------------------------- Bausteine

@Composable
private fun monoStyle(size: TextUnit = 11.sp) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, letterSpacing = 0.6.sp)

@Composable
private fun Eyebrow(text: String, atlas: Atlas) = Text(text.uppercase(Locale.GERMAN), color = atlas.inkSoft, style = monoStyle())

@Composable
private fun Headline(text: String, atlas: Atlas, accent: String? = null) {
    val style = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 36.sp, color = atlas.ink)
    if (accent == null || !text.contains(accent)) {
        Text(text, style = style)
    } else {
        val before = text.substringBefore(accent)
        val after = text.substringAfter(accent)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                append(before)
                pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic, color = atlas.visited))
                append(accent)
                pop()
                append(after)
            },
            style = style,
        )
    }
}

@Composable
private fun Soft(text: String, atlas: Atlas, size: TextUnit = 13.sp, maxLines: Int = Int.MAX_VALUE) =
    Text(text, color = atlas.inkSoft, fontSize = size, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

/** 0 → 1, sobald die Seite sichtbar wird (bei reduzierter Bewegung bzw. beim Aufnehmen sofort 1). */
@Composable
private fun rememberReveal(active: Boolean, instant: Boolean, durationMs: Int = 900): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(active, instant) {
        when {
            instant -> anim.snapTo(1f)
            active -> { anim.snapTo(0f); anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
        }
    }
    return anim.value
}

@Composable
private fun Stat(value: Int, label: String, reveal: Float, atlas: Atlas, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            String.format(Locale.GERMAN, "%,d", (value * reveal).roundToInt()),
            style = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 34.sp, color = atlas.ink),
        )
        Soft(label, atlas, 12.sp)
    }
}

@Composable
private fun Foot(left: String, right: String, atlas: Atlas) {
    Column {
        HorizontalDivider(color = atlas.rule)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(left, color = atlas.inkSoft, style = monoStyle())
            Text(right, color = atlas.inkSoft, style = monoStyle())
        }
    }
}

/** Karte aus den App-eigenen Länderumrissen; besuchte Länder eingefärbt, Routen als Bögen, die sich aufzeichnen. */
@Composable
private fun AtlasMap(
    shapes: List<CountryShape>,
    highlight: Set<String>,
    box: FloatArray,
    atlas: Atlas,
    aspect: Float,
    routes: List<List<Pair<Double, Double>>> = emptyList(),
    progress: Float = 1f,
) {
    val territories = ReferenceData.territoryToCountry
    Canvas(
        Modifier.fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(6.dp))
            .drawWithCache {
                val (lonMin, latMin, lonMax, latMax) = box.toList()
                fun project(lat: Double, lon: Double) = Offset(
                    ((lon - lonMin) / (lonMax - lonMin) * size.width).toFloat(),
                    ((latMax - lat) / (latMax - latMin) * size.height).toFloat(),
                )
                val paths = shapes.map { shape ->
                    val path = Path().apply {
                        fillType = PathFillType.EvenOdd
                        shape.polygons.forEach { polygon ->
                            polygon.forEach { ring ->
                                ring.forEachIndexed { i, p ->
                                    val o = project(p[1], p[0])
                                    if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                                }
                                close()
                            }
                        }
                    }
                    val iso = territories[shape.iso2] ?: shape.iso2
                    path to (shape.iso2 in highlight || iso in highlight)
                }
                onDrawBehind {
                    drawRect(atlas.sea)
                    paths.forEach { (path, on) ->
                        drawPath(path, if (on) atlas.visited else atlas.land)
                        drawPath(path, atlas.sheet, style = Stroke(width = 0.5f))
                    }
                }
            },
    ) {
        if (routes.isEmpty()) return@Canvas
        val (lonMin, latMin, lonMax, latMax) = box.toList()
        fun project(p: Pair<Double, Double>) = Offset(
            ((p.second - lonMin) / (lonMax - lonMin) * size.width).toFloat(),
            ((latMax - p.first) / (latMax - latMin) * size.height).toFloat(),
        )
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        routes.forEach { route ->
            route.zipWithNext().forEach { (a, b) ->
                val p1 = project(a)
                val p2 = project(b)
                val dist = (p2 - p1).getDistance()
                val control = Offset((p1.x + p2.x) / 2, (p1.y + p2.y) / 2 - dist * 0.18f)
                val steps = 28
                val upTo = (steps * progress).roundToInt().coerceAtLeast(1)
                val path = Path()
                for (s in 0..upTo) {
                    val u = s / steps.toFloat()
                    val x = (1 - u) * (1 - u) * p1.x + 2 * (1 - u) * u * control.x + u * u * p2.x
                    val y = (1 - u) * (1 - u) * p1.y + 2 * (1 - u) * u * control.y + u * u * p2.y
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, atlas.route, style = stroke)
            }
            if (progress > 0.98f) route.forEach { drawCircle(atlas.route, 2.2.dp.toPx(), project(it)) }
        }
    }
}

private val WORLD = floatArrayOf(-170f, -56f, 180f, 78f)

/** Ausschnitt um alle Routen (mit Rand), mindestens 40° breit; Seitenverhältnis wird über die Höhe angepasst. */
private fun routeBox(routes: List<List<Pair<Double, Double>>>, aspect: Float): FloatArray {
    val pts = routes.flatten()
    if (pts.isEmpty()) return floatArrayOf(-15f, 30f, 40f, 62f)
    var lonMin = pts.minOf { it.second }.toFloat(); var lonMax = pts.maxOf { it.second }.toFloat()
    var latMin = pts.minOf { it.first }.toFloat(); var latMax = pts.maxOf { it.first }.toFloat()
    val padLon = maxOf(6f, (lonMax - lonMin) * 0.12f); val padLat = maxOf(5f, (latMax - latMin) * 0.2f)
    lonMin -= padLon; lonMax += padLon; latMin -= padLat; latMax += padLat
    if (lonMax - lonMin < 40f) { val c = (lonMin + lonMax) / 2; lonMin = c - 20f; lonMax = c + 20f }
    // Breite/Höhe an das Seitenverhältnis anpassen (äquirektangulär)
    val wantLat = (lonMax - lonMin) / aspect
    if (latMax - latMin < wantLat) { val c = (latMin + latMax) / 2; latMin = c - wantLat / 2; latMax = c + wantLat / 2 }
    else { val wantLon = (latMax - latMin) * aspect; val c = (lonMin + lonMax) / 2; lonMin = c - wantLon / 2; lonMax = c + wantLon / 2 }
    return floatArrayOf(lonMin, latMin.coerceAtLeast(-85f), lonMax, latMax.coerceAtMost(85f))
}

// ---------------------------------------------------------------- Seiten

@Composable
private fun ColumnScope.TitlePage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas, active: Boolean, instant: Boolean) {
    val s = data.stats
    val reveal = rememberReveal(active, instant)
    Eyebrow(stringResource(R.string.review_title_eyebrow), atlas)
    Text(data.year.toString(), style = TextStyle(fontFamily = Serif, fontSize = 104.sp, lineHeight = 92.sp, color = atlas.ink))
    AtlasMap(state.shapes, s.countries.toSet(), WORLD, atlas, aspect = 1.9f)
    Row(Modifier.fillMaxWidth()) {
        Stat(s.countries.size, stringResource(R.string.stats_countries), reveal, atlas, Modifier.weight(1f))
        Stat(s.trips, stringResource(R.string.stats_trips), reveal, atlas, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth()) {
        Stat(s.nightsAway, stringResource(R.string.stats_nights_away), reveal, atlas, Modifier.weight(1f))
        Stat((s.details.totalKm).roundToInt(), stringResource(R.string.review_km_air), reveal, atlas, Modifier.weight(1f))
    }
    Spacer(Modifier.weight(1f))
    val c = state.homeCoordinates
    Foot(
        c?.let { String.format(Locale.GERMAN, "%.2f° %s · %.2f° %s", abs(it.first), if (it.first >= 0) "N" else "S", abs(it.second), if (it.second >= 0) "O" else "W") }.orEmpty(),
        state.homeName.orEmpty(), atlas,
    )
}

private val numberWords = listOf("Kein", "Einmal", "Zwei Mal", "Drei Mal", "Vier Mal", "Fünf Mal", "Sechs Mal", "Sieben Mal", "Acht Mal", "Neun Mal", "Zehn Mal", "Elf Mal", "Zwölf Mal")

@Composable
private fun ColumnScope.TripsPage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas, active: Boolean, instant: Boolean) {
    val progress = rememberReveal(active, instant, 1400)
    val trips = data.trips
    Eyebrow(stringResource(R.string.review_trips_eyebrow), atlas)
    val count = trips.size
    Headline(if (count == 0) stringResource(R.string.review_no_trips) else "${numberWords.getOrElse(count) { "$count Mal" }} weg", atlas, accent = "weg")
    val aspect = 2.15f
    AtlasMap(state.shapes, data.stats.countries.toSet(), routeBox(state.routes, aspect), atlas, aspect, state.routes, progress)
    // Zeitstrahl Jän–Dez
    BoxWithConstraints(Modifier.fillMaxWidth().height(86.dp).border(width = 0.5.dp, color = atlas.rule)) {
        val w = maxWidth
        val start = LocalDate.of(data.year, 1, 1)
        val days = start.lengthOfYear().toFloat()
        val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
        months.forEachIndexed { i, m ->
            val x = w * (ChronoUnit.DAYS.between(start, start.withMonth(i + 1)) / days)
            Box(Modifier.offset(x = x).width(0.5.dp).fillMaxHeight().background(atlas.rule))
            Text(m, color = atlas.inkSoft, style = monoStyle(9.sp), modifier = Modifier.offset(x = x + 3.dp, y = 70.dp))
        }
        trips.forEachIndexed { i, t ->
            val a = ChronoUnit.DAYS.between(start, t.start) / days
            val b = (ChronoUnit.DAYS.between(start, t.end) + 1) / days
            val isWork = t.baseTypeIds.any { state.editor.typesById[it]?.group == StayGroup.WORK }
            Box(
                Modifier.offset(x = w * a, y = (8 + (i % 3) * 20).dp)
                    .width(maxOf(w * (b - a), 18.dp)).height(16.dp)
                    .background(if (isWork) atlas.dienst else atlas.visited, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(t.countryIso2s.mapNotNull { ReferenceData.country(it)?.flag }.firstOrNull().orEmpty(), fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        trips.take(7).forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.countryIso2s.mapNotNull { ReferenceData.country(it)?.flag }.joinToString(""), modifier = Modifier.width(44.dp), maxLines = 1)
                Text(tripTitle(t, state.editor), color = atlas.ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.review_days_short, t.days), color = atlas.inkSoft, style = monoStyle())
            }
        }
        if (trips.size > 7) Soft(stringResource(R.string.stats_more_n, trips.size - 7), atlas, 12.sp)
    }
}

@Composable
private fun ColumnScope.ColorsPage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas, active: Boolean, instant: Boolean) {
    val reveal = rememberReveal(active, instant, 1100)
    val editor = state.editor
    val today = LocalDate.now()
    val start = LocalDate.of(data.year, 1, 1)
    val kinds = (0 until start.lengthOfYear()).associate { i ->
        val date = start.plusDays(i.toLong())
        date to if (date.isAfter(today)) DayKind.LEER else dayKind(data.stats.mainTypeByDate[date], editor)
    }
    val counts = kinds.values.filter { it != DayKind.LEER }.groupingBy { it }.eachCount()
    val total = counts.values.sum().coerceAtLeast(1)
    val vacation = (counts[DayKind.URLAUB] ?: 0) * 100 / total
    Eyebrow(stringResource(R.string.review_colors_eyebrow), atlas)
    if (vacation > 0) Headline(stringResource(R.string.review_colors_title, vacation), atlas, accent = stringResource(R.string.review_colors_accent))
    else Headline(stringResource(R.string.review_colors_eyebrow), atlas)
    // Heatmap: eine Zeile je Monat
    val months = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..12).forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(months[m - 1], color = atlas.inkSoft, style = monoStyle(9.sp), modifier = Modifier.width(12.dp))
                val len = start.withMonth(m).lengthOfMonth()
                (1..31).forEach { d ->
                    val visible = d <= len
                    val kind = if (visible) kinds.getValue(start.withMonth(m).withDayOfMonth(d)) else DayKind.LEER
                    val order = ((m - 1) * 31 + d) / 372f
                    val shown = reveal >= order
                    Box(
                        Modifier.weight(1f).aspectRatio(1f)
                            .background(if (visible && shown) atlas.color(kind) else Color.Transparent, RoundedCornerShape(2.dp))
                            .then(if (visible && kind == DayKind.LEER) Modifier.border(0.5.dp, atlas.rule, RoundedCornerShape(2.dp)) else Modifier),
                    )
                }
            }
        }
    }
    // Ring + Legende
    val ordered = listOf(DayKind.ALLTAG, DayKind.ARBEIT, DayKind.AUSBILDUNG, DayKind.ZUHAUSE, DayKind.AUSFLUG, DayKind.URLAUB, DayKind.DIENST, DayKind.UNTERWEGS)
        .filter { (counts[it] ?: 0) > 0 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                var angle = -90f
                ordered.forEach { kind ->
                    val sweep = 360f * (counts[kind] ?: 0) / total * reveal
                    drawArc(atlas.color(kind), angle, sweep, false, topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                    angle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(total.toString(), style = TextStyle(fontFamily = Serif, fontSize = 26.sp, color = atlas.ink))
                Text(stringResource(R.string.review_days_caps), color = atlas.inkSoft, style = monoStyle(9.sp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ordered.forEach { kind ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(atlas.color(kind), RoundedCornerShape(2.dp)))
                    Text(" " + kindLabel(kind, data.includeWork), color = atlas.ink, fontSize = 12.sp, modifier = Modifier.weight(1f, fill = false))
                    Text("  ${(counts[kind] ?: 0) * 100 / total} %", color = atlas.inkSoft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PlacesPage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas, active: Boolean, instant: Boolean) {
    val reveal = rememberReveal(active, instant)
    val editor = state.editor
    val s = data.stats
    Eyebrow(stringResource(R.string.review_places_eyebrow), atlas)
    Headline(stringResource(R.string.review_places_title), atlas, accent = stringResource(R.string.review_places_accent))
    val max = (s.topCities.maxOfOrNull { it.second } ?: 1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        s.topCities.forEach { (id, days) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(editor.placeName(id), color = atlas.ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.review_days_long, days), color = atlas.inkSoft, fontSize = 13.sp)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(atlas.rule, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(days / max * reveal).fillMaxHeight().background(atlas.visited, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
    val top = s.details.topPlaces.take(3)
    if (top.isNotEmpty()) {
        Eyebrow(stringResource(R.string.review_top_places), atlas)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            top.forEach { (id, n) ->
                Row {
                    Text(categoryGlyph(data.mainCategoryByPlace[id]), modifier = Modifier.width(28.dp))
                    Text(editor.placeName(id), color = atlas.ink, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$n×", color = atlas.inkSoft, fontSize = 13.sp)
                }
            }
        }
    }
    val newCities = s.details.newCityIds
    if (newCities.isNotEmpty()) {
        Row {
            Box(Modifier.width(2.dp).height(54.dp).background(atlas.route))
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = atlas.ink))
                    append(stringResource(R.string.review_new_cities_n, newCities.size))
                    pop()
                    append(" ")
                    append(newCities.take(10).joinToString(", ") { editor.placeName(it) })
                    if (newCities.size > 10) append(" …")
                },
                color = atlas.inkSoft, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.MomentsPage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas) {
    val editor = state.editor
    val r = data.stats.details.records
    data class Moment(val glyph: String, val title: String, val detail: String)
    fun country(iso: String?) = iso?.let { ReferenceData.country(it) }
    val moments = buildList {
        data.birthday?.let { b ->
            val c = country(b.countryIso2)
            add(Moment("✦", stringResource(R.string.review_m_birthday, editor.placeName(b.placeId)), listOfNotNull(Format.mediumDate(b.date), c?.let { "${it.flag} ${it.nameDe}" }).joinToString(" · ")))
        }
        r.longestTrip?.let { t ->
            add(Moment("◷", stringResource(R.string.review_m_longest, tripTitle(t, editor)), stringResource(R.string.review_m_longest_detail, t.days, t.basePlaceIds.size)))
        }
        r.farthestPlaceId?.let { id ->
            add(Moment("⌖", stringResource(R.string.review_m_farthest, editor.placeName(id)), stringResource(R.string.review_m_farthest_detail, km(r.farthestMeters), state.homeName.orEmpty())))
        }
        if (data.firstVisits.isNotEmpty()) {
            val names = data.firstVisits.mapNotNull { country(it.countryIso2)?.nameDe }
            val youngest = data.firstVisits.first()
            add(Moment("◐", stringResource(R.string.review_m_first, names.joinToString(", ")),
                stringResource(R.string.review_m_first_detail, country(youngest.countryIso2)?.nameDe.orEmpty(), youngest.age)))
        }
        data.newYearsEve?.let { n ->
            val c = country(n.countryIso2)
            add(Moment("✳", stringResource(R.string.review_m_nye, editor.placeName(n.placeId)), listOfNotNull(Format.mediumDate(n.date), c?.let { "${it.flag} ${it.nameDe}" }).joinToString(" · ")))
        }
    }
    Eyebrow(stringResource(R.string.review_moments_eyebrow), atlas)
    Headline(stringResource(R.string.review_moments_title), atlas, accent = stringResource(R.string.review_moments_accent))
    if (moments.isEmpty()) Soft(stringResource(R.string.review_m_none), atlas)
    Column {
        moments.forEachIndexed { i, m ->
            Row(Modifier.padding(vertical = 10.dp)) {
                Text(m.glyph, style = TextStyle(fontFamily = Serif, fontSize = 26.sp, color = atlas.route), modifier = Modifier.width(36.dp))
                Column {
                    Text(m.title, color = atlas.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Soft(m.detail, atlas, 12.5.sp)
                }
            }
            if (i < moments.lastIndex) HorizontalDivider(color = atlas.rule)
        }
    }
}

@Composable
private fun ColumnScope.TogetherPage(state: YearReviewUiState, data: YearReviewData, atlas: Atlas, active: Boolean, instant: Boolean) {
    val reveal = rememberReveal(active, instant)
    val partner = state.editor.persons.firstOrNull { !it.isMe }
    if (partner != null && data.togetherDays > 0) {
        Eyebrow(stringResource(R.string.review_together_eyebrow), atlas)
        Text((data.togetherDays * reveal).roundToInt().toString(), style = TextStyle(fontFamily = Serif, fontSize = 76.sp, lineHeight = 70.sp, color = atlas.ink))
        Soft(stringResource(R.string.review_together_text, partner.name, data.togetherTrips, data.trips.size), atlas, 14.sp)
    }
    data.previous?.let { p ->
        val s = data.stats
        Spacer(Modifier.height(4.dp))
        Eyebrow(stringResource(R.string.review_compare_title, data.year - 1), atlas)
        @Composable
        fun Line(label: String, before: Int, now: Int, header: Boolean = false) {
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                val style = if (header) monoStyle(10.sp) else TextStyle(fontSize = 13.sp)
                Text(label, color = if (header) atlas.inkSoft else atlas.ink, style = style, modifier = Modifier.weight(1f))
                Text(if (header) (data.year - 1).toString() else before.toString(), color = atlas.inkSoft, style = style, modifier = Modifier.width(52.dp))
                Text(if (header) data.year.toString() else now.toString(), color = atlas.ink, style = style, modifier = Modifier.width(52.dp))
                val d = now - before
                Text(
                    if (header) "" else if (d > 0) "▲ $d" else if (d < 0) "▼ ${-d}" else "±0",
                    color = if (d >= 0) atlas.visited else atlas.route, style = style, modifier = Modifier.width(48.dp),
                )
            }
        }
        Line("", 0, 0, header = true)
        Line(stringResource(R.string.stats_countries), p.countries.size, s.countries.size)
        Line(stringResource(R.string.stats_trips), p.trips, s.trips)
        Line(stringResource(R.string.stats_nights_away), p.nightsAway, s.nightsAway)
        Line(stringResource(R.string.stats_new_cities), p.newCities.size, s.newCities.size)
    }
    Spacer(Modifier.weight(1f))
    Foot(stringResource(R.string.review_end), "locus", atlas)
}
