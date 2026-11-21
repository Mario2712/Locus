package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.day.StayPriority
import io.github.mario2712.locus.domain.model.DayTemplate
import io.github.mario2712.locus.domain.model.EntryDefaults
import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayGroup
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.model.TemplateStep
import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.model.VisitCategory
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.DayTemplateRepository
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import io.github.mario2712.locus.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

/** Nachschlagedaten, die alle Editoren brauchen. */
data class EditorData(
    val places: List<Place> = emptyList(),
    val stayTypes: List<StayType> = emptyList(),
    val persons: List<Person> = emptyList(),
    val visitCategories: List<VisitCategory> = emptyList(),
    val templates: List<DayTemplate> = emptyList(),
    val defaults: EntryDefaults = EntryDefaults(),
    /** Eigene Reisenamen je Reise-ID (ID der ersten Basis-Station). */
    val tripNames: Map<String, String> = emptyMap(),
) {
    val placesById: Map<String, Place> by lazy { places.associateBy { it.id } }
    val typesById: Map<String, StayType> by lazy { stayTypes.associateBy { it.id } }
    val categoriesById: Map<String, VisitCategory> by lazy { visitCategories.associateBy { it.id } }
    val templatesById: Map<String, DayTemplate> by lazy { templates.associateBy { it.id } }
    /** Aktuelle Homebase (spätester Beginn). */
    val homebase: Place? by lazy { places.firstOrNull { it.isHomebase } }
    /** Homebase-Zeiträume (Umzüge). */
    val homebases: io.github.mario2712.locus.domain.timeline.Homebases by lazy { io.github.mario2712.locus.domain.timeline.Homebases.of(places) }
    /** Homebase, die am [date] galt. */
    fun homebaseAt(date: java.time.LocalDate): Place? = homebases.at(date)?.let(placesById::get)
    val rank: (String) -> Int by lazy { StayPriority.ranker(typesById) }

    /** Aktive Arten für Auswahllisten. */
    val activeStayTypes: List<StayType> get() = stayTypes.filterNot { it.isArchived }
    val activeCategories: List<VisitCategory> get() = visitCategories.filterNot { it.isArchived }

    /** Arten, die eine Reise beginnen können (Urlaub, Dienstreise …). */
    val tripTypes: List<StayType>
        get() = activeStayTypes.filter { it.group == StayGroup.VACATION || it.id == "stay-business-trip" }

    /** Städte und Gemeinden. */
    val cities: List<Place> get() = sortedPlaces.filter { it.kind == PlaceKind.CITY }

    /** Favoriten zuerst, dann alphabetisch. */
    val sortedPlaces: List<Place>
        get() = places.filter { it.kind != PlaceKind.COUNTRY }.sortedWith(
            compareByDescending<Place> { it.isHomebase }.thenByDescending { it.isFavorite }.thenBy { it.displayName.lowercase() },
        )

    fun placeName(id: String?): String = id?.let { placesById[it]?.displayName } ?: "?"
    fun typeName(id: String?): String = id?.let { typesById[it]?.name } ?: "?"
    fun typeColor(id: String?): Long = id?.let { typesById[it]?.colorArgb } ?: 0xFF888888
    fun categoryName(id: String): String = categoriesById[id]?.name ?: "?"

    fun participantsLabel(ids: Set<PersonId>): String =
        persons.filter { it.id in ids }.joinToString(" & ") { it.name }

    fun stepLabel(step: TemplateStep): String = "${typeName(step.stayTypeId)} · ${placeName(step.placeId)}"

    fun stepsLabel(steps: List<TemplateStep>): String = steps.joinToString(" → ") { stepLabel(it) }

    /** Standardwert für „Ich übernachte dort“: an bei Urlaub, sonst aus. */
    fun defaultOvernight(typeId: String?): Boolean = typeId?.let { typesById[it]?.group } == StayGroup.VACATION

    fun stayItem(stay: Stay) = StayItem(
        stay = stay,
        typeName = typeName(stay.typeId),
        typeColor = typeColor(stay.typeId),
        placeName = placeName(stay.placeId),
        participants = participantsLabel(stay.participants),
    )

    fun visitItem(visit: Visit) = VisitItem(
        visit = visit,
        placeName = placeName(visit.placeId),
        categories = visit.categoryIds.joinToString(" + ") { categoryName(it) },
        participants = participantsLabel(visit.participants),
    )

    /** Ob ein Besuch nach Kategorie-Regel für Länderpunkte zählt (ohne Override). */
    fun categoriesCount(ids: Set<String>): Boolean = ids.any { categoriesById[it]?.countsForCountryPoints == true }
}

data class StayItem(
    val stay: Stay,
    val typeName: String,
    val typeColor: Long,
    val placeName: String,
    val participants: String,
)

data class VisitItem(
    val visit: Visit,
    val placeName: String,
    val categories: String,
    val participants: String,
)

/** Eingaben für einen neuen Ort (manuell; Suche über OpenStreetMap folgt in M3). */
data class PlaceDraft(
    val name: String,
    val countryIso2: String,
    val regionCode: String?,
    val alias: String?,
    val isFavorite: Boolean,
    /** Stadt/Gemeinde oder Örtlichkeit (Lokal, Gipfel, Museum …). */
    val kind: PlaceKind = PlaceKind.CITY,
    /** Zugehörige Stadt einer Örtlichkeit. */
    val parentPlaceId: String? = null,
)

class EditorCatalog @Inject constructor(
    private val placeRepository: PlaceRepository,
    catalogRepository: CatalogRepository,
    personRepository: PersonRepository,
    templateRepository: DayTemplateRepository,
    settingsRepository: SettingsRepository,
    tripRepository: io.github.mario2712.locus.domain.repository.TripRepository,
) {
    private val base = combine(
        placeRepository.observeAll(),
        catalogRepository.observeStayTypes(),
        personRepository.observeAll(),
        catalogRepository.observeVisitCategories(),
    ) { places, types, persons, categories -> EditorData(places, types, persons, categories) }

    val data: Flow<EditorData> = combine(
        base,
        templateRepository.observeAll(),
        settingsRepository.observeEntryDefaults(),
        tripRepository.observeAll(),
    ) { data, templates, defaults, trips ->
        data.copy(templates = templates, defaults = defaults, tripNames = trips.associate { it.id to it.title })
    }

    suspend fun createPlace(draft: PlaceDraft): Place {
        val place = Place(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            kind = draft.kind,
            countryIso2 = draft.countryIso2,
            regionCode = draft.regionCode,
            alias = draft.alias?.trim()?.takeIf { it.isNotEmpty() },
            isFavorite = draft.isFavorite,
            parentPlaceId = draft.parentPlaceId.takeIf { draft.kind != PlaceKind.CITY },
        )
        placeRepository.upsert(place)
        return place
    }
}
