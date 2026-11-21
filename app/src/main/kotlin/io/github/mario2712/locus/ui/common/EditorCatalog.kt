package io.github.mario2712.locus.ui.common

import io.github.mario2712.locus.domain.model.Person
import io.github.mario2712.locus.domain.model.PersonId
import io.github.mario2712.locus.domain.model.Place
import io.github.mario2712.locus.domain.model.PlaceKind
import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.StayType
import io.github.mario2712.locus.domain.repository.CatalogRepository
import io.github.mario2712.locus.domain.repository.PersonRepository
import io.github.mario2712.locus.domain.repository.PlaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID
import javax.inject.Inject

/** Nachschlagedaten, die alle Editoren brauchen. */
data class EditorData(
    val places: List<Place> = emptyList(),
    val stayTypes: List<StayType> = emptyList(),
    val persons: List<Person> = emptyList(),
) {
    val placesById: Map<String, Place> by lazy { places.associateBy { it.id } }
    val typesById: Map<String, StayType> by lazy { stayTypes.associateBy { it.id } }
    val personsById: Map<PersonId, Person> by lazy { persons.associateBy { it.id } }

    /** Aktive Arten für Auswahllisten. */
    val activeStayTypes: List<StayType> get() = stayTypes.filterNot { it.isArchived }

    /** Favoriten zuerst, dann alphabetisch. */
    val sortedPlaces: List<Place>
        get() = places.sortedWith(compareByDescending<Place> { it.isFavorite }.thenBy { it.displayName.lowercase() })

    fun placeName(id: String?): String = id?.let { placesById[it]?.displayName } ?: "?"
    fun typeName(id: String?): String = id?.let { typesById[it]?.name } ?: "?"
    fun typeColor(id: String?): Long = id?.let { typesById[it]?.colorArgb } ?: 0xFF888888

    fun participantsLabel(ids: Set<PersonId>): String =
        persons.filter { it.id in ids }.joinToString(" & ") { it.name }

    fun stayItem(stay: Stay) = StayItem(
        stay = stay,
        typeName = typeName(stay.typeId),
        typeColor = typeColor(stay.typeId),
        placeName = placeName(stay.placeId),
        participants = participantsLabel(stay.participants),
    )
}

data class StayItem(
    val stay: Stay,
    val typeName: String,
    val typeColor: Long,
    val placeName: String,
    val participants: String,
)

/** Eingaben für einen neuen Ort (M2: manuell; Suche über OpenStreetMap folgt in M3). */
data class PlaceDraft(
    val name: String,
    val countryIso2: String,
    val regionCode: String?,
    val alias: String?,
    val isFavorite: Boolean,
)

class EditorCatalog @Inject constructor(
    private val placeRepository: PlaceRepository,
    catalogRepository: CatalogRepository,
    personRepository: PersonRepository,
) {
    val data: Flow<EditorData> = combine(
        placeRepository.observeAll(),
        catalogRepository.observeStayTypes(),
        personRepository.observeAll(),
    ) { places, types, persons -> EditorData(places, types, persons) }

    suspend fun createPlace(draft: PlaceDraft): Place {
        val place = Place(
            id = UUID.randomUUID().toString(),
            name = draft.name.trim(),
            kind = PlaceKind.CITY,
            countryIso2 = draft.countryIso2,
            regionCode = draft.regionCode,
            alias = draft.alias?.trim()?.takeIf { it.isNotEmpty() },
            isFavorite = draft.isFavorite,
        )
        placeRepository.upsert(place)
        return place
    }
}
