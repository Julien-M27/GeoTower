package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class FhLinkMatcherTest {

    @Test
    fun `matches reciprocal link and prefers nearest relay on same axis`() {
        val latA = 48.0000
        val lonA = 0.2000
        val latB = 48.0500
        val lonB = 0.2000
        val latC = 48.2000
        val lonC = 0.2000

        val bearingAB = FhLinkMatcher.initialBearingDeg(latA, lonA, latB, lonB)
        val bearingBA = FhLinkMatcher.initialBearingDeg(latB, lonB, latA, lonA)
        val bearingCA = FhLinkMatcher.initialBearingDeg(latC, lonC, latA, lonA)

        val result = FhLinkMatcher.match(
            listOf(
                FhLinkMatcher.FhSite("A", latA, lonA, "OP1", listOf(bearingAB)),
                FhLinkMatcher.FhSite("B", latB, lonB, "OP1", listOf(bearingBA)),
                FhLinkMatcher.FhSite("C", latC, lonC, "OP1", listOf(bearingCA)),
            ),
        )

        assertEquals(1, result.links.size)
        val link = result.links.single()
        assertEquals("A", link.idAnfrA)
        assertEquals("B", link.idAnfrB)
        assertTrue(link.distanceKm in 5.0..6.0)
    }

    @Test
    fun `does not match across different operators`() {
        val bearingAB = FhLinkMatcher.initialBearingDeg(48.0, 0.2, 48.05, 0.25)
        val bearingBA = FhLinkMatcher.initialBearingDeg(48.05, 0.25, 48.0, 0.2)

        val result = FhLinkMatcher.match(
            listOf(
                FhLinkMatcher.FhSite("A", 48.0, 0.2, "OP1", listOf(bearingAB)),
                FhLinkMatcher.FhSite("B", 48.05, 0.25, "OP2", listOf(bearingBA)),
            ),
        )

        assertEquals(0, result.links.size)
        assertEquals(2, result.unmatchedAzimuths)
    }

    @Test
    fun `matches long distance link accounting for meridian convergence`() {
        // Liaison Est-Ouest de ~95 km (Le Mans -> Angers/Tours environ)
        val latA = 48.0000
        val lonA = 0.2000
        val latB = 48.0000
        val lonB = 1.4800

        val bearingAB = FhLinkMatcher.initialBearingDeg(latA, lonA, latB, lonB)
        val bearingBA = FhLinkMatcher.initialBearingDeg(latB, lonB, latA, lonA)

        val result = FhLinkMatcher.match(
            listOf(
                FhLinkMatcher.FhSite("A", latA, lonA, "OP1", listOf(bearingAB)),
                FhLinkMatcher.FhSite("B", latB, lonB, "OP1", listOf(bearingBA)),
            ),
        )

        assertEquals(1, result.links.size)
        val link = result.links.single()
        assertTrue("Expected ~95 km, got ${link.distanceKm}", link.distanceKm in 90.0..100.0)
        assertEquals(48.0, link.minLat, 0.0001)
        assertEquals(0.2, link.minLon, 0.0001)
        assertEquals(1.48, link.maxLon, 0.0001)
    }

    @Test
    fun `probe real anfr database if present`() {
        val candidates = listOf(
            File("build/tmp/anfr_extra_db_size_probe/geotower_fr_extra_non_mobile_test.db"),
            File("../build/tmp/anfr_extra_db_size_probe/geotower_fr_extra_non_mobile_test.db"),
        )
        val dbFile = candidates.firstOrNull { it.exists() } ?: return

        val sites = ArrayList<FhLinkMatcher.FhSite>()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val tables = conn.createStatement().use { s ->
                s.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { rs ->
                    generateSequence { if (rs.next()) rs.getString("name") else null }.toSet()
                }
            }
            if ("localisation" !in tables) {
                println("Skipping real DB probe: tables=${tables.sorted()}")
                return
            }
            val hasAdmId = conn.createStatement().use { s ->
                s.executeQuery("PRAGMA table_info(technique)").use { rs ->
                    generateSequence { if (rs.next()) rs.getString("name") else null }
                        .any { it == "adm_id" }
                }
            }
            val sql = if (hasAdmId) {
                """
                SELECT l.id_anfr, l.latitude, l.longitude,
                       COALESCE(CAST(l.operateur_id AS TEXT), 'adm_' || COALESCE(CAST(t.adm_id AS TEXT), '0')) AS grp,
                       l.azimuts_fh
                FROM localisation l
                LEFT JOIN technique t ON l.id_anfr = t.id_anfr
                WHERE l.azimuts_fh IS NOT NULL AND l.azimuts_fh != ''
                """.trimIndent()
            } else {
                """
                SELECT id_anfr, latitude, longitude,
                       COALESCE(CAST(operateur_id AS TEXT), '0') AS grp,
                       azimuts_fh
                FROM localisation
                WHERE azimuts_fh IS NOT NULL AND azimuts_fh != ''
                """.trimIndent()
            }
            conn.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        val rawAz = rs.getString("azimuts_fh") ?: continue
                        val azs = rawAz.split(",").mapNotNull { it.trim().toDoubleOrNull() }
                        if (azs.isEmpty()) continue
                        sites.add(
                            FhLinkMatcher.FhSite(
                                idAnfr = rs.getString("id_anfr"),
                                lat = rs.getDouble("latitude"),
                                lon = rs.getDouble("longitude"),
                                groupKey = rs.getString("grp"),
                                azimuths = azs,
                            ),
                        )
                    }
                }
            }
        }

        val result = FhLinkMatcher.match(sites)
        val under5 = result.links.count { it.distanceKm < 5.0 }
        val d5to20 = result.links.count { it.distanceKm in 5.0..20.0 }
        val d20to50 = result.links.count { it.distanceKm > 20.0 && it.distanceKm <= 50.0 }
        val d50to100 = result.links.count { it.distanceKm > 50.0 && it.distanceKm <= 100.0 }
        val over100 = result.links.count { it.distanceKm > 100.0 }
        val maxLink = result.links.maxByOrNull { it.distanceKm }
        val leMansLinks = result.links.filter {
            it.maxLat >= 47.92 && it.minLat <= 48.06 && it.maxLon >= 0.12 && it.minLon <= 0.28
        }

        println(
            """
            |=== FH PROTOTYPE REAL DB PROBE ===
            |DB: ${dbFile.path}
            |FH sites: ${result.totalInputSites}, FH azimuths: ${result.totalInputAzimuths}
            |Matched links: ${result.links.size} (matched azimuths: ${result.matchedAzimuths}, unmatched: ${result.unmatchedAzimuths})
            |Elapsed: ${result.elapsedMs} ms
            |Distances: <5km=$under5, 5-20km=$d5to20, 20-50km=$d20to50, 50-100km=$d50to100, >100km=$over100
            |Max link: ${maxLink?.let { "${it.idAnfrA}<->${it.idAnfrB} (${"%.1f".format(it.distanceKm)} km, grp=${it.groupKey})" }}
            |Le Mans bbox links: ${leMansLinks.size}
            """.trimMargin(),
        )
        assertTrue(result.links.isNotEmpty())
    }
}
