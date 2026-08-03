package fr.geotower.data.outages

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class LocalOutageGeneratorTest {

    private val validCsv =
        "code_site_op;station_anfr;latitude;longitude;commune\n0010000001;0010000001;48.85;2.35;PARIS\n"

    private object NoopGeocoder : SiteGeocoder {
        override suspend fun bestMatch(
            operatorKey: String,
            codeInsee: String?,
            lat: Double,
            lon: Double,
            sameInseeThresholdMeters: Double,
            spatialThresholdMeters: Double,
        ): GeocodeMatch? = null
    }

    @Test
    fun fetcherCollectsRowsAndPerOperatorErrors() = runBlocking {
        val fetcher = OperatorOutageFetcher { url ->
            if (url == OperatorOutageSource.ORANGE.url) throw IOException("HTTP 503")
            validCsv.toByteArray()
        }
        val result = fetcher.fetchAll()
        assertEquals(3, result.rowsBySource.size) // free, sfr, bouygues OK
        assertTrue(OperatorOutageSource.ORANGE in result.downloadErrors)
        assertFalse(OperatorOutageSource.SFR in result.downloadErrors)
    }

    @Test
    fun generatesSitesFromOperatorFiles() = runBlocking {
        val fetcher = OperatorOutageFetcher { validCsv.toByteArray() }
        val result = LocalOutageGenerator(fetcher, NoopGeocoder).generate("2026-07-13")
        // 4 opérateurs × 1 ligne à station explicite → 4 pannes, aucune géocodée.
        assertEquals(4, result.sites.size)
        assertTrue(result.sites.all { it.idAnfr == "0010000001" })
        assertEquals("2026-07-13", result.sites.first().sourceLastUpdate)
    }

    /**
     * Base ANFR absente ou illisible : le géocodage n'est qu'un enrichissement, les pannes doivent
     * quand même sortir (avec les coordonnées brutes de l'opérateur), et la raison être remontée.
     */
    @Test
    fun keepsOutagesWhenGeocoderFails() = runBlocking {
        // Code site non convertible en identifiant ANFR → le géocodeur est bien sollicité.
        val csvWithoutStation = "code_site_op;lat;lon;commune\nABC123;48.85;2.35;PARIS\n"
        val failingGeocoder = object : SiteGeocoder {
            override suspend fun bestMatch(
                operatorKey: String,
                codeInsee: String?,
                lat: Double,
                lon: Double,
                sameInseeThresholdMeters: Double,
                spatialThresholdMeters: Double,
            ): GeocodeMatch = throw IllegalStateException("base ANFR absente")
        }
        val fetcher = OperatorOutageFetcher { csvWithoutStation.toByteArray() }
        val result = LocalOutageGenerator(fetcher, failingGeocoder).generate()

        assertEquals(4, result.sites.size) // 4 opérateurs × 1 ligne, aucune perdue
        assertEquals("base ANFR absente", result.stats.geocodeError)
        assertTrue(result.sites.all { it.latitude == 48.85 })
    }

    @Test
    fun throwsWhenNoOperatorAvailable() = runBlocking {
        val fetcher = OperatorOutageFetcher { throw IOException("down") }
        try {
            LocalOutageGenerator(fetcher, NoopGeocoder).generate()
            fail("devrait lever quand aucun opérateur n'est disponible")
        } catch (e: IOException) {
            // attendu : sortie inchangée
        }
    }
}
