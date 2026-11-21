package io.github.mario2712.locus.domain.usecase

import java.time.LocalDate

/** Gesetzliche Feiertage in Österreich (bundesweit). Karfreitag ist seit 2019 kein Feiertag mehr. */
object AustrianHolidays {

    fun of(year: Int): Map<LocalDate, String> {
        val easter = easterSunday(year)
        return sortedMapOf(
            LocalDate.of(year, 1, 1) to "Neujahr",
            LocalDate.of(year, 1, 6) to "Heilige Drei Könige",
            easter.plusDays(1) to "Ostermontag",
            LocalDate.of(year, 5, 1) to "Staatsfeiertag",
            easter.plusDays(39) to "Christi Himmelfahrt",
            easter.plusDays(50) to "Pfingstmontag",
            easter.plusDays(60) to "Fronleichnam",
            LocalDate.of(year, 8, 15) to "Mariä Himmelfahrt",
            LocalDate.of(year, 10, 26) to "Nationalfeiertag",
            LocalDate.of(year, 11, 1) to "Allerheiligen",
            LocalDate.of(year, 12, 8) to "Mariä Empfängnis",
            LocalDate.of(year, 12, 25) to "Christtag",
            LocalDate.of(year, 12, 26) to "Stefanitag",
        )
    }

    fun nameOf(date: LocalDate): String? = of(date.year)[date]

    fun isHoliday(date: LocalDate): Boolean = nameOf(date) != null

    /** Ostersonntag nach der Gaußschen Osterformel (anonymer gregorianischer Algorithmus). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
