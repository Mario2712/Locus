package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.di.ApplicationScope
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.repository.StayRepository
import io.github.mario2712.locus.domain.repository.VisitRepository
import io.github.mario2712.locus.domain.timeline.ImplicitHome
import io.github.mario2712.locus.domain.timeline.Timeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Ein Stand aller Einträge samt aufgelöster Timeline – einmal berechnet, von allen Reitern geteilt. */
class TimelineSnapshot(
    val stays: List<Stay>,
    val visits: List<Visit>,
    val editor: EditorData,
    val today: LocalDate,
    /** Aufgelöste Stationen (laufende Basen verlängert). */
    val resolved: List<Stay>,
) {
    /** Mit implizitem Zuhause (für Orts- und Städtestatistik), je Person einmal berechnet. */
    private val withHomeCache = HashMap<io.github.mario2712.locus.domain.model.PersonId, List<Stay>>()

    fun withImplicitHome(person: io.github.mario2712.locus.domain.model.PersonId): List<Stay> = synchronized(withHomeCache) {
        withHomeCache.getOrPut(person) { ImplicitHome.augment(resolved, visits, person, editor.homebases, today) }
    }
}

/**
 * Gemeinsamer Zwischenspeicher: Die Timeline wird pro Datenänderung **einmal** aufgelöst statt in jedem Reiter
 * (Kalender, Timeline, Reisen, Orte, Länder, Karte, Statistik) separat. Bleibt eine Minute nach dem letzten
 * Beobachter erhalten, damit Reiterwechsel sofort Daten haben.
 */
@Singleton
class TimelineCache @Inject constructor(
    stays: StayRepository,
    visits: VisitRepository,
    editorCatalog: EditorCatalog,
    @ApplicationScope scope: CoroutineScope,
) {
    val snapshot: Flow<TimelineSnapshot> = combine(stays.observeAll(), visits.observeAll(), editorCatalog.data) { s, v, editor ->
        val today = LocalDate.now()
        TimelineSnapshot(s, v, editor, today, Timeline.resolve(s, editor.homebases, today))
    }
        .conflate()
        .flowOn(Dispatchers.Default)
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 60_000), replay = 1)
}
