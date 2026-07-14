package fr.geotower.data.outages

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du cœur « génération locale des pannes » (T1), sans réseau ni base : le géocodage est
 * simulé par [FakeGeocoder]. Miroir de `docs/server/test_build_sites_hs.py`.
 */
class SitesHsLocalBuilderTest {

    /** Géocodeur en mémoire reproduisant la logique du géocodeur serveur (INSEE puis spatial). */
    private class FakeGeocoder(private val sites: List<Site>) : SiteGeocoder {
        data class Site(
            val operatorKey: String,
            val stationAnfr: String,
            val lat: Double,
            val lon: Double,
            val codeInsee: String?,
        )

        override suspend fun bestMatch(
            operatorKey: String,
            codeInsee: String?,
            lat: Double,
            lon: Double,
            sameInseeThresholdMeters: Double,
            spatialThresholdMeters: Double,
        ): GeocodeMatch? {
            if (codeInsee != null) {
                var best: GeocodeMatch? = null
                for (site in sites.filter { it.operatorKey == operatorKey && it.codeInsee == codeInsee }) {
                    val distance = haversineMeters(lat, lon, site.lat, site.lon)
                    if (distance <= sameInseeThresholdMeters && (best == null || distance < best.distanceMeters)) {
                        best = GeocodeMatch(site.stationAnfr, distance, "db_insee")
                    }
                }
                if (best != null) return best
            }
            var best: GeocodeMatch? = null
            for (site in sites.filter { it.operatorKey == operatorKey }) {
                val distance = haversineMeters(lat, lon, site.lat, site.lon)
                if (distance <= spatialThresholdMeters && (best == null || distance < best.distanceMeters)) {
                    best = GeocodeMatch(site.stationAnfr, distance, "db_spatial")
                }
            }
            return best
        }
    }

    private val sfrRow1 = mapOf(
        "code_site_op" to "SI0001", "station_anfr" to "0751234567",
        "latitude" to "48.8566", "longitude" to "2.3522",
        "departement" to "Paris", "code_postal" to "75001", "code_insee" to "75101", "commune" to "PARIS",
        "2gvoix" to "HS", "3gvoix" to "OK", "4gvoix" to "OK", "3gdata" to "OK", "4gdata" to "OK",
        "voix" to "HS", "data" to "OK", "propre" to "oui", "raison" to "INT", "detail" to "Incident en cours",
        "debut" to "2026-01-01 10:00:00", "fin" to "2026-07-01",
    )

    // Pas de station → à géocoder via la base.
    private val sfrRow2 = mapOf(
        "code_site_op" to "SI0002", "station_anfr" to "",
        "latitude" to "48.8600", "longitude" to "2.3400",
        "code_insee" to "75102", "commune" to "PARIS",
        "4gvoix" to "HS", "voix" to "HS", "data" to "OK",
        "debut" to "2026-02-01", "fin" to "2026-08-01",
    )

    // Doublon de la ligne 1 (même station) avec fenêtre de dates plus large.
    private val sfrRow3 = sfrRow1 + mapOf("debut" to "2025-12-15", "fin" to "2026-07-15")

    private val freeRow = mapOf(
        "code_site_op" to "75056_ABC", "latitude" to "48.8", "longitude" to "2.3",
        "commune" to "PARIS", "code_insee" to "75000",
        "3gvoix" to "OK", "4gvoix" to "HS", "voix" to "HS", "data" to "HS",
        "raison" to "MAINT", "debut" to "2026-03-01", "fin" to "2026-09-01",
    )

    private fun geocoder() = FakeGeocoder(
        listOf(FakeGeocoder.Site("sfr", "0759999999", 48.8601, 2.3401, "75102")),
    )

    @Test
    fun buildsOperatorOnlyListWithGeocodingDedupAndMerge() {
        val result = runBlocking {
            SitesHsLocalBuilder().build(
                rowsBySource = mapOf(
                    OperatorOutageSource.SFR to listOf(sfrRow1, sfrRow2, sfrRow3),
                    OperatorOutageSource.FREE to listOf(freeRow),
                ),
                geocoder = geocoder(),
                sourceLastUpdate = "2026-07-13",
            )
        }

        // 2 features SFR (dont 1 dédupliquée) + 1 Free.
        assertEquals(3, result.sites.size)
        assertEquals(mapOf("sfr" to 3, "free" to 1), result.stats.operatorRows)
        assertEquals(mapOf("sfr" to 2), result.stats.stationFromOperator)
        assertEquals(mapOf("sfr" to 1), result.stats.stationFromDb)
        assertEquals(mapOf("free" to 1), result.stats.withoutStation)
        assertEquals(mapOf("sfr" to 1), result.stats.duplicatesMerged)
        assertEquals(3, result.stats.outputFeatures)

        val byStation = result.sites.associateBy { it.operateur to it.idAnfr }

        // 1) Station fournie par l'opérateur ; la fusion du doublon a élargi les dates.
        val sfrOp = byStation.getValue("SFR" to "0751234567")
        assertEquals(1, sfrOp.propre)
        assertEquals("HS", sfrOp.voix2g)
        assertEquals("2025-12-15", sfrOp.dateDebut)
        assertEquals("2026-07-15", sfrOp.dateFin)
        assertEquals(48.8566, sfrOp.latitude, 1e-9)
        assertEquals("2026-07-13", sfrOp.sourceLastUpdate)
        assertTrue(!sfrOp.isPotential)

        // 2) Station retrouvée via le géocodeur.
        val sfrDb = byStation.getValue("SFR" to "0759999999")
        assertEquals("SFR", sfrDb.operateur)

        // 3) Free : pas de station, INSEE dérivé du code site, code postal en repli.
        val free = result.sites.first { it.operateur == "Free Mobile" }
        assertEquals("", free.idAnfr)
        assertEquals("75056", free.codeInsee)
        assertEquals("75000", free.codePostal)
    }

    @Test
    fun rowWithoutCoordinatesIsSkipped() {
        assertNull(normalizeOperatorRow(OperatorOutageSource.SFR, mapOf("code_site_op" to "X", "commune" to "PARIS")))
    }

    @Test
    fun freeInseeFromCodeSiteExtractsPrefix() {
        assertEquals("75056", freeInseeFromCodeSite("75056_1"))
        assertNull(freeInseeFromCodeSite("ABC_1"))
        assertNull(freeInseeFromCodeSite(null))
    }

    @Test
    fun noStationWhenGeocoderFindsNothing() {
        // Aucun site Free dans le géocodeur → la panne Free reste sans station mais est publiée.
        val result = runBlocking {
            SitesHsLocalBuilder().build(
                rowsBySource = mapOf(OperatorOutageSource.FREE to listOf(freeRow)),
                geocoder = geocoder(),
            )
        }
        assertEquals(1, result.sites.size)
        assertEquals("", result.sites.single().idAnfr)
        assertEquals(mapOf("free" to 1), result.stats.withoutStation)
    }
}
