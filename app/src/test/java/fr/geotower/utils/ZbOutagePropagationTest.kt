package fr.geotower.utils

import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZbOutagePropagationTest {

    private fun antenna(
        idAnfr: String,
        operateur: String,
        isZb: Int,
        lat: Double = 48.85,
        lon: Double = 2.35
    ) = LocalisationEntity(
        idAnfr = idAnfr,
        operateur = operateur,
        latitude = lat,
        longitude = lon,
        azimuts = null,
        codeInsee = null,
        azimutsFh = null,
        isZb = isZb
    )

    private fun declaredHs(idAnfr: String, operateur: String) = SiteHsEntity(
        idAnfr = idAnfr,
        operateur = operateur,
        latitude = 48.85,
        longitude = 2.35
    )

    @Test
    fun zbSiteWithOneDeclaredOutagePropagatesToOtherZbOperators() {
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 1),
            antenna("1002", "SFR", isZb = 1),
            antenna("1003", "Bouygues Telecom", isZb = 1)
        )
        val declared = listOf(declaredHs("1001", "Orange"))

        val potentials = zbPotentialOutagesForSite(antennas, declared)

        // Orange garde sa panne déclarée ; SFR + Bouygues passent en « potentiellement en panne ».
        assertEquals(2, potentials.size)
        assertTrue(potentials.all { it.isPotential })
        assertEquals(setOf("1002", "1003"), potentials.map { it.idAnfr }.toSet())
    }

    @Test
    fun declaredOperatorKeepsRealOutageAndIsNotDuplicated() {
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 1),
            antenna("1002", "SFR", isZb = 1)
        )
        val declared = listOf(declaredHs("1001", "Orange"))

        val potentials = zbPotentialOutagesForSite(antennas, declared)

        assertFalse(potentials.any { it.idAnfr == "1001" })
    }

    @Test
    fun noPropagationWhenSiteIsNotZb() {
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 0),
            antenna("1002", "SFR", isZb = 0)
        )
        val declared = listOf(declaredHs("1001", "Orange"))

        assertTrue(zbPotentialOutagesForSite(antennas, declared).isEmpty())
    }

    @Test
    fun noPropagationWhenNoDeclaredOutage() {
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 1),
            antenna("1002", "SFR", isZb = 1)
        )

        assertTrue(zbPotentialOutagesForSite(antennas, emptyList()).isEmpty())
    }

    @Test
    fun onlyZbOperatorsOnSiteAreMarkedPotential() {
        // Site en zone blanche mais un opérateur non-ZB y est présent : on ne propage qu'aux ZB.
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 1),
            antenna("1002", "SFR", isZb = 1),
            antenna("1003", "Free Mobile", isZb = 0)
        )
        val declared = listOf(declaredHs("1001", "Orange"))

        val potentials = zbPotentialOutagesForSite(antennas, declared)

        assertEquals(setOf("1002"), potentials.map { it.idAnfr }.toSet())
    }

    @Test
    fun multiSiteVariantGroupsByPhysicalLocation() {
        // Site A (ZB, déclaré HS) et site B (ZB, sans panne) à des coordonnées différentes.
        val antennas = listOf(
            antenna("1001", "Orange", isZb = 1, lat = 48.8500, lon = 2.3500),
            antenna("1002", "SFR", isZb = 1, lat = 48.8500, lon = 2.3500),
            antenna("2001", "Orange", isZb = 1, lat = 45.7600, lon = 4.8400),
            antenna("2002", "SFR", isZb = 1, lat = 45.7600, lon = 4.8400)
        )
        val declared = listOf(declaredHs("1001", "Orange"))

        val potentials = zbPotentialOutages(antennas, declared)

        // Seul le site A (qui a une panne) propage vers son voisin SFR ; le site B reste intact.
        assertEquals(setOf("1002"), potentials.map { it.idAnfr }.toSet())
    }
}
