package io.github.mario2712.locus.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

/**
 * Datum als ISO-Text (yyyy-MM-dd), damit SQL-Vergleiche lexikografisch funktionieren und
 * Exporte lesbar bleiben. Uhrzeit als HH:mm.
 */
class Converters {
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)
    @TypeConverter fun timeToString(value: LocalTime?): String? = value?.withSecond(0)?.withNano(0)?.toString()
    @TypeConverter fun stringToTime(value: String?): LocalTime? = value?.let(LocalTime::parse)
}
