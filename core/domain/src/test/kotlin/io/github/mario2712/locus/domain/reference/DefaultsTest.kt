package io.github.mario2712.locus.domain.reference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DefaultsTest {

    @Test
    fun `IDs sind eindeutig`() {
        assertEquals(Defaults.stayTypes.size, Defaults.stayTypes.map { it.id }.toSet().size)
        assertEquals(Defaults.visitCategories.size, Defaults.visitCategories.map { it.id }.toSet().size)
        assertEquals(Defaults.persons.size, Defaults.persons.map { it.id }.toSet().size)
    }

    @Test
    fun `Transit, Anreise und Tankstopp zaehlen nicht fuer Laenderpunkte`() {
        val types = Defaults.stayTypes.associateBy { it.id }
        assertFalse(types.getValue(Defaults.StayTypeIds.TRANSIT).countsForCountryPoints)
        assertFalse(types.getValue(Defaults.StayTypeIds.ON_THE_WAY).countsForCountryPoints)
        val notCounting = Defaults.visitCategories.filterNot { it.countsForCountryPoints }.map { it.id }
        assertEquals(listOf(Defaults.VisitCategoryIds.REST_STOP), notCounting)
    }

    @Test
    fun `genau eine Person ist ich`() {
        assertEquals(listOf(Defaults.ME), Defaults.persons.filter { it.isMe }.map { it.id })
    }
}
