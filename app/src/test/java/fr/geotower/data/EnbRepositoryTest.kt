package fr.geotower.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnbRepositoryTest {

    @Test
    fun siteNetworkIdsDefaultsAndProperties() {
        val empty = EnbRepository.SiteNetworkIds()
        assertTrue(empty.isEmpty)
        assertFalse(empty.isEstimated)

        val direct = EnbRepository.SiteNetworkIds(lte = listOf(12345L), nr = emptyList(), isEstimated = false)
        assertFalse(direct.isEmpty)
        assertFalse(direct.isEstimated)
        assertEquals(listOf(12345L), direct.lte)

        val estimated = EnbRepository.SiteNetworkIds(lte = listOf(54321L), nr = listOf(98765L), isEstimated = true)
        assertFalse(estimated.isEmpty)
        assertTrue(estimated.isEstimated)
    }

    @Test
    fun distanceMetersE6Calculation() {
        // Points identiques = 0 m
        val zero = EnbRepository.distanceMetersE6(48856600L, 2352200L, 48856600L, 2352200L)
        assertEquals(0.0, zero, 0.001)

        // ~100 micro-degrés en latitude ≈ 11.1 mètres
        val distLat = EnbRepository.distanceMetersE6(48856600L, 2352200L, 48856700L, 2352200L)
        assertTrue(distLat in 11.0..11.2)

        // Seuil d'estimation respecté
        assertTrue(distLat <= EnbRepository.MAX_ESTIMATION_DISTANCE_METERS)
    }

    @Test
    fun plmnForOperatorRecognizesMetropolitanOperators() {
        assertEquals("20801", EnbRepository.plmnForOperator("ORANGE"))
        assertEquals("20815", EnbRepository.plmnForOperator("FREE MOBILE"))
        assertEquals("20820", EnbRepository.plmnForOperator("BOUYGUES"))
        assertEquals("20810", EnbRepository.plmnForOperator("SFR"))
        assertEquals(null, EnbRepository.plmnForOperator("AUTRE"))
        assertEquals(null, EnbRepository.plmnForOperator(null))
    }
}
