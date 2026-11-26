package io.github.mario2712.locus.data.backup

import kotlinx.serialization.Serializable

/**
 * Austauschformat (JSON). Enthält alle Datensätze inkl. Löschmarkierungen und Sync-Metadaten,
 * damit ein Import Stände zusammenführen kann (neuerer `updatedAt` gewinnt).
 * Datum als ISO-Text, Uhrzeit als HH:mm, Aufzählungen als Name.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val schemaVersion: Int,
    val exportedAt: Long,
    val device: String,
    val persons: List<PersonDto> = emptyList(),
    val stayTypes: List<StayTypeDto> = emptyList(),
    val visitCategories: List<VisitCategoryDto> = emptyList(),
    val regionCollections: List<RegionCollectionDto> = emptyList(),
    val places: List<PlaceDto> = emptyList(),
    val stays: List<StayDto> = emptyList(),
    val visits: List<VisitDto> = emptyList(),
    val trips: List<TripDto> = emptyList(),
    val wishes: List<WishDto> = emptyList(),
    val patternRules: List<PatternRuleDto> = emptyList(),
    val dayTemplates: List<DayTemplateDto> = emptyList(),
    /** Geräteeinstellungen (Standardwerte, Erinnerung, Auto-Backup) – optional, ab Schema 5. */
    val settings: SettingsDto? = null,
) {
    companion object {
        const val FORMAT = "locus-backup"
    }
}

/** Einstellungen, die beim Umzug auf ein anderes Gerät bzw. eine andere App-Variante mitkommen. */
@Serializable
data class SettingsDto(
    val prefillTransport: Boolean = true,
    val dailyParticipants: List<String> = emptyList(),
    val tripParticipants: List<String> = emptyList(),
    val documentedSinceYear: Int = 2014,
    val sortByUsage: Boolean = false,
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 20 * 60 + 30,
    val autoBackup: Boolean = true,
)

@Serializable
data class MetaDto(val createdAt: Long, val updatedAt: Long, val deleted: Boolean, val originDevice: String)

@Serializable
data class PersonDto(val id: String, val name: String, val colorArgb: Long, val isMe: Boolean, val birthDate: String? = null, val meta: MetaDto)

@Serializable
data class StayTypeDto(
    val id: String, val name: String, val group: String, val countsForCountryPoints: Boolean, val icon: String,
    val colorArgb: Long, val sortOrder: Int, val isArchived: Boolean, val meta: MetaDto,
)

@Serializable
data class VisitCategoryDto(
    val id: String, val name: String, val countsForCountryPoints: Boolean, val icon: String,
    val sortOrder: Int, val isArchived: Boolean, val meta: MetaDto,
)

@Serializable
data class RegionCollectionDto(val countryIso2: String, val enabled: Boolean, val meta: MetaDto)

@Serializable
data class PlaceDto(
    val id: String, val name: String, val kind: String, val countryIso2: String, val regionCode: String? = null,
    val latitude: Double? = null, val longitude: Double? = null, val cityName: String? = null,
    val parentPlaceId: String? = null, val osmId: String? = null, val poiCategory: String? = null,
    val alias: String? = null, val isFavorite: Boolean = false, val note: String? = null,
    val isHomebase: Boolean = false, val meta: MetaDto, val address: String? = null,
    val homebaseSince: String? = null,
)

@Serializable
data class StayDto(
    val id: String, val placeId: String, val typeId: String, val startDate: String, val endDate: String,
    val startTime: String? = null, val endTime: String? = null, val precision: String, val sortIndex: Int,
    val tripId: String? = null, val note: String? = null, val source: String, val overnight: Boolean = false,
    val transport: String = "", val persons: List<String>, val meta: MetaDto,
)

@Serializable
data class VisitDto(
    val id: String, val placeId: String, val categoryId: String, val date: String, val time: String? = null,
    val precision: String, val countsOverride: Boolean? = null, val tripId: String? = null, val note: String? = null,
    val rating: Int? = null, val photoUri: String? = null, val source: String, val extraCategoryIds: String = "",
    val persons: List<String>, val meta: MetaDto,
)

@Serializable
data class TripDto(
    val id: String, val title: String, val startDate: String, val endDate: String, val startTime: String? = null,
    val endTime: String? = null, val precision: String, val note: String? = null, val persons: List<String>, val meta: MetaDto,
)

@Serializable
data class WishDto(
    val id: String, val placeId: String? = null, val countryIso2: String? = null, val personId: String? = null,
    val note: String? = null, val priority: Int = 0, val wishedAt: Long, val fulfilledVisitId: String? = null, val meta: MetaDto,
)

@Serializable
data class PatternRuleDto(
    val id: String, val personId: String, val weekday: Int, val stayTypeId: String, val placeId: String,
    val validFrom: String? = null, val validTo: String? = null, val templateId: String? = null, val meta: MetaDto,
)

@Serializable
data class DayTemplateDto(val id: String, val name: String, val steps: String, val sortOrder: Int, val meta: MetaDto)

/** Ergebnis eines Imports. */
data class ImportResult(val added: Int, val updated: Int, val unchanged: Int)
