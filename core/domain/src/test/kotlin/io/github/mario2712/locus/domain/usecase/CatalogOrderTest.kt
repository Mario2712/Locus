package io.github.mario2712.locus.domain.usecase

import io.github.mario2712.locus.domain.model.Visit
import io.github.mario2712.locus.domain.reference.Defaults
import io.github.mario2712.locus.domain.reference.Defaults.VisitCategoryIds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CatalogOrderTest {
    private val d = LocalDate.parse("2026-05-01")
    private fun visit(main: String, vararg extra: String) =
        Visit("v", "p", main, d, participants = setOf(Defaults.ME), extraCategoryIds = extra.toSet())

    @Test
    fun `Gipfel wird zu Natur, ohne doppelte Kategorie`() {
        val summit = CatalogOrder.replaceCategory(visit(VisitCategoryIds.SUMMIT), VisitCategoryIds.SUMMIT, VisitCategoryIds.NATURE)!!
        assertEquals(setOf(VisitCategoryIds.NATURE), summit.categoryIds)

        val both = CatalogOrder.replaceCategory(
            visit(VisitCategoryIds.NATURE, VisitCategoryIds.SUMMIT, VisitCategoryIds.RESTAURANT),
            VisitCategoryIds.SUMMIT, VisitCategoryIds.NATURE,
        )!!
        assertEquals(VisitCategoryIds.NATURE, both.categoryId)
        assertEquals(setOf(VisitCategoryIds.RESTAURANT), both.extraCategoryIds)

        val extra = CatalogOrder.replaceCategory(visit(VisitCategoryIds.RESTAURANT, VisitCategoryIds.SUMMIT), VisitCategoryIds.SUMMIT, VisitCategoryIds.NATURE)!!
        assertEquals(listOf(VisitCategoryIds.RESTAURANT, VisitCategoryIds.NATURE), extra.categoryIds.toList())

        assertNull(CatalogOrder.replaceCategory(visit(VisitCategoryIds.MUSEUM), VisitCategoryIds.SUMMIT, VisitCategoryIds.NATURE))
    }

    @Test
    fun `verschieben und nach Haeufigkeit sortieren`() {
        assertEquals(listOf("b", "c", "a"), CatalogOrder.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), CatalogOrder.move(listOf("a", "b", "c"), 2, 0))
        val visits = listOf(visit("x"), visit("y", "x"), visit("y").copy(date = d.minusYears(2)))
        val usage = CatalogOrder.categoryUsage(visits, d.minusYears(1))
        assertEquals(mapOf("x" to 2, "y" to 1), usage)
        assertEquals(listOf("x", "y", "z"), CatalogOrder.byUsage(listOf("z", "y", "x"), usage) { it })
    }
}
