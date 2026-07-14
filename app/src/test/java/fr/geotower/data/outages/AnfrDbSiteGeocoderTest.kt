package fr.geotower.data.outages

import fr.geotower.data.models.OutageGeocodeSiteRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de la LOGIQUE de géocodage (INSEE puis spatial, seuils, filtrage opérateur, cache),
 * la source de sites étant simulée — donc sans Room ni device.
 */
class AnfrDbSiteGeocoderTest {

    private class FakeSource(
        private val byInsee: Map<String, List<OutageGeocodeSiteRow>>,
        private val all: List<OutageGeocodeSiteRow>,
    ) : OutageSiteSource {
        var inseeQueryCount = 0
        var boxQueryCount = 0

        override suspend fun sitesByInsee(codeInsee: String): List<OutageGeocodeSiteRow> {
            inseeQueryCount++
            return byInsee[codeInsee] ?: emptyList()
        }

        override suspend fun sitesInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<OutageGeocodeSiteRow> {
            boxQueryCount++
            return all.filter { it.latitude in minLat..maxLat && it.longitude in minLon..maxLon }
        }
    }

    private fun site(id: String, lat: Double, lon: Double, operateur: String) =
        OutageGeocodeSiteRow(idAnfr = id, latitude = lat, longitude = lon, operateur = operateur)

    @Test
    fun matchesWithinSameInsee() = runBlocking {
        val source = FakeSource(
            byInsee = mapOf("75102" to listOf(site("0759999999", 48.8601, 2.3401, "SFR"))),
            all = emptyList(),
        )
        val match = AnfrDbSiteGeocoder(source).bestMatch("sfr", "75102", 48.8600, 2.3400, 600.0, 400.0)
        assertEquals("0759999999", match?.stationAnfr)
        assertEquals("db_insee", match?.matchType)
    }

    @Test
    fun fallsBackToSpatialWhenNoInsee() = runBlocking {
        val nearby = site("0750000002", 48.8502, 2.3502, "Orange")
        val source = FakeSource(byInsee = emptyMap(), all = listOf(nearby))
        val match = AnfrDbSiteGeocoder(source).bestMatch("orange", null, 48.8500, 2.3500, 600.0, 400.0)
        assertEquals("0750000002", match?.stationAnfr)
        assertEquals("db_spatial", match?.matchType)
    }

    @Test
    fun returnsNullBeyondThreshold() = runBlocking {
        val far = site("0750000003", 48.9000, 2.4000, "Orange") // ~6 km
        val source = FakeSource(byInsee = mapOf("99999" to listOf(far)), all = listOf(far))
        val match = AnfrDbSiteGeocoder(source).bestMatch("orange", "99999", 48.8500, 2.3500, 600.0, 400.0)
        assertNull(match)
    }

    @Test
    fun ignoresOtherOperators() = runBlocking {
        // Un site Orange proche ne doit jamais servir à géolocaliser une panne SFR.
        val orange = site("0750000004", 48.8501, 2.3501, "Orange")
        val source = FakeSource(byInsee = mapOf("75056" to listOf(orange)), all = listOf(orange))
        val match = AnfrDbSiteGeocoder(source).bestMatch("sfr", "75056", 48.8500, 2.3500, 600.0, 400.0)
        assertNull(match)
    }

    @Test
    fun cachesInseeQueriesAcrossCalls() = runBlocking {
        val source = FakeSource(
            byInsee = mapOf("75102" to listOf(site("0759999999", 48.8601, 2.3401, "SFR"))),
            all = emptyList(),
        )
        val geocoder = AnfrDbSiteGeocoder(source)
        geocoder.bestMatch("sfr", "75102", 48.8600, 2.3400, 600.0, 400.0)
        geocoder.bestMatch("sfr", "75102", 48.8600, 2.3400, 600.0, 400.0)
        assertEquals(1, source.inseeQueryCount) // 2e appel servi par le cache
    }
}
