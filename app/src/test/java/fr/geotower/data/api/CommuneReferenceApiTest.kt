package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommuneReferenceApiTest {
    @Test
    fun parsesGeoApiCommuneReferenceAndConvertsHectaresToSquareKilometres() {
        val reference = CommuneReferenceApi.parse(
            """{"code":"19123","nom":"Tulle","population":14500,"surface":2430.5}""",
            requestedCode = "19123"
        )

        requireNotNull(reference)
        assertEquals("19123", reference.codeInsee)
        assertEquals("Tulle", reference.name)
        assertEquals(24.305, reference.areaKm2!!, 0.0001)
        assertEquals(14500, reference.population)
        assertNull(reference.populationYear)
    }

    @Test
    fun keepsRequestedCodeWhenResponseOmitsCode() {
        val reference = CommuneReferenceApi.parse(
            """{"nom":"Tulle","population":0,"surface":0}""",
            requestedCode = "19123"
        )

        requireNotNull(reference)
        assertEquals("19123", reference.codeInsee)
        assertEquals(0, reference.population)
        assertEquals(0.0, reference.areaKm2!!, 0.0001)
    }

    @Test
    fun aggregatesDepartmentCommunesForAdministrativeReferenceFallback() {
        val totals = AdministrativeAreaReferenceApi.parseCommunesTotals(
            """
            [
              {"population": 1200, "surface": 1500.0},
              {"population": 800, "surface": 500.0},
              {"population": null, "surface": 25.0}
            ]
            """
        )

        requireNotNull(totals)
        assertEquals(2000, totals.population)
        assertEquals(2025.0, totals.surfaceHectares, 0.0001)
    }
}
