package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Stay
import io.github.mario2712.locus.domain.model.Visit
import java.time.LocalDate

/** Reihenfolge und Pflege von Aufenthaltsarten und Besuchskategorien. */
object CatalogOrder {

    /** Verschiebt ein Element von [from] nach [to]. */
    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Häufigkeit je Aufenthaltsart seit [since]. */
    fun stayTypeUsage(stays: List<Stay>, since: LocalDate): Map<String, Int> =
        stays.filter { !it.startDate.isBefore(since) }.groupingBy { it.typeId }.eachCount()

    /** Häufigkeit je Besuchskategorie (auch Zusatzkategorien) seit [since]. */
    fun categoryUsage(visits: List<Visit>, since: LocalDate): Map<String, Int> =
        visits.filter { !it.date.isBefore(since) }.flatMap { it.categoryIds }.groupingBy { it }.eachCount()

    /** Häufig genutzte zuerst; bei Gleichstand bleibt die feste Reihenfolge von [items]. */
    fun <T> byUsage(items: List<T>, usage: Map<String, Int>, id: (T) -> String): List<T> =
        items.sortedByDescending { usage[id(it)] ?: 0 }

    /**
     * Ersetzt die Kategorie [from] durch [to] (z. B. „Gipfel“ → „Natur / Wandern“).
     * @return geänderter Besuch oder `null`, wenn [from] nicht vorkommt.
     */
    fun replaceCategory(visit: Visit, from: String, to: String): Visit? {
        if (from !in visit.categoryIds) return null
        val ids = visit.categoryIds.map { if (it == from) to else it }.distinct()
        return visit.copy(categoryId = ids.first(), extraCategoryIds = ids.drop(1).toSet())
    }
}
