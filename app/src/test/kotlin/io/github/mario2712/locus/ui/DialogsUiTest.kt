package io.github.mario2712.locus.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.mario2712.locus.R
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.TransportMode
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.StayTypeIds
import io.github.mario2712.locus.domain.timeline.TimelineDay
import io.github.mario2712.locus.ui.common.EditorData
import io.github.mario2712.locus.ui.editor.ReturnDraft
import io.github.mario2712.locus.ui.editor.ReturnHomeDialog
import io.github.mario2712.locus.ui.editor.StationEditorDialog
import io.github.mario2712.locus.ui.editor.StayDraft
import io.github.mario2712.locus.ui.timeline.TimelineDayRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

/** UI-Tests der wichtigsten Dialoge (Robolectric, ohne Emulator). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DialogsUiTest {
    @get:Rule
    val rule = createComposeRule()

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private val day = LocalDate.parse("2026-06-23")
    private val editor = EditorData(
        places = listOf(
            Place("dfw", "Flughafen Dallas", PlaceKind.POI, "US"),
            Place("hotel", "Comfort Suites", PlaceKind.POI, "US"),
        ),
        stayTypes = Defaults.stayTypes,
        persons = Defaults.persons,
        visitCategories = Defaults.visitCategories,
    )

    @Test
    fun heimreiseMitAnkunftAmFolgetag() {
        var result: ReturnDraft? = null
        rule.setContent {
            ReturnHomeDialog("Zuhause", setOf(TransportMode.CAR), editor, onDismiss = {}, onConfirm = { result = it }, onCreatePlace = { error("nicht erwartet") })
        }
        rule.onNodeWithTag("home-next-day").performClick()
        rule.onNodeWithText(str(R.string.action_save)).performClick()
        assertEquals(1, result?.homeDayOffset)
        assertEquals(setOf(TransportMode.CAR), result?.homeTransport)
        assertEquals(emptyList<Any>(), result?.stops)
    }

    @Test
    fun stationDavorEinfuegenAmVortag() {
        var saved: StayDraft? = null
        val draft = StayDraft(
            startDate = day, typeId = StayTypeIds.TRANSIT, placeId = "dfw", participants = setOf(Defaults.ME),
            insertAnchor = day, insertIndex = 1, dateOptions = listOf(day.minusDays(1), day),
        )
        rule.setContent {
            StationEditorDialog(draft, editor, onDismiss = {}, onSave = { saved = it }, onDelete = null, onCreatePlace = { error("nicht erwartet") })
        }
        rule.onNodeWithText(str(R.string.editor_previous_day), substring = true).performClick()
        rule.onNodeWithText(str(R.string.action_save)).performClick()
        assertEquals(day.minusDays(1), saved?.startDate)
        assertEquals(day.minusDays(1), saved?.endDate)
        assertEquals(1, saved?.insertIndex)
    }

    @Test
    fun timelineZeigtAusgangspunktUndStationen() {
        val base = Stay("h", "hotel", StayTypeIds.VACATION, day.minusDays(4), day, participants = setOf(Defaults.ME), overnight = true)
        val transit = Stay("t", "dfw", StayTypeIds.TRANSIT, day, participants = setOf(Defaults.ME), transport = setOf(TransportMode.TAXI))
        rule.setContent {
            TimelineDayRow(TimelineDay(day, base, listOf(transit), emptyList()), editor, label = "Di., 23. Juni") {}
        }
        rule.onNodeWithText("Comfort Suites", substring = true).assertExists()
        rule.onNodeWithText("Flughafen Dallas", substring = true).assertExists()
    }
}
