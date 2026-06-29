package fr.geotower.data.coverage

import fr.geotower.data.models.AntenneDbEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests du moteur de couverture théorique (réseau simulé, code pur).
 * Couvre : plaine, colline masquante, courbure, secteur vs omni, down-tilt, obstacles,
 * union de deux antennes opposées, exclusion des faisceaux hertziens.
 */
class CoverageEngineTest {

    // ----------------------------------------------------------------- helpers

    private fun distances(step: Double, maxR: Double): DoubleArray {
        val n = (maxR / step).toInt()
        return DoubleArray(n) { (it + 1) * step }
    }

    private fun field(
        bearings: List<Double>,
        distances: DoubleArray,
        siteGround: Double = 0.0,
        maxRadius: Double = distances.last(),
        ground: (bearing: Double, index: Int, distance: Double) -> Double
    ): TerrainField {
        val rays = bearings.map { b ->
            TerrainRay(b, distances.copyOf(), DoubleArray(distances.size) { i -> ground(b, i, distances[i]) })
        }
        return TerrainField(45.0, 5.0, siteGround, rays, distances[0], maxRadius, obstaclesIncluded = false)
    }

    private fun omni(h: Double = 30.0) = AntennaSpec("omni", 0.0, h, omni = true, halfBeamDeg = 180.0, frequencyMHz = null, operator = null, typeLabel = null)
    private fun sector(az: Double, h: Double = 30.0) = AntennaSpec("sec", az, h, omni = false, halfBeamDeg = 59.0, frequencyMHz = null, operator = null, typeLabel = null)
    private fun params(maxR: Double, tilt: Double = 0.0, curvature: Boolean = true) =
        ViewshedParams(maxRadiusM = maxR, curvature = curvature, tiltDeg = tilt)

    // -------------------------------------------------------------- geo basics

    @Test
    fun destinationPointMatchesRequestedDistance() {
        val p = CoverageGeo.destinationPoint(45.0, 5.0, 90.0, 1000.0)
        val back = CoverageGeo.haversineMeters(45.0, 5.0, p[0], p[1])
        assertEquals(1000.0, back, 1.0)
    }

    @Test
    fun horizontalHalfWidthFollowsThreshold() {
        // 65 * sqrt(10/12) ≈ 59.3°
        assertEquals(59.3, CoverageGeo.horizontalHalfWidthDeg(65.0, 30.0, -10.0), 0.5)
    }

    @Test
    fun signedDeltaHandlesWraparound() {
        assertEquals(-10.0, CoverageGeo.signedDeltaDeg(350.0, 0.0), 1e-9)
        assertEquals(10.0, CoverageGeo.signedDeltaDeg(10.0, 0.0), 1e-9)
        assertEquals(20.0, CoverageGeo.signedDeltaDeg(10.0, 350.0), 1e-9)
        assertEquals(-170.0, CoverageGeo.signedDeltaDeg(180.0, 350.0), 1e-9)
    }

    @Test
    fun northSectorOutlineIsContiguousAcrossZero() {
        val d = distances(100.0, 3000.0)
        val bearings = (0 until 360 step 10).map { it.toDouble() }
        val f = field(bearings, d) { _, _, _ -> 0.0 }
        // Antenne plein nord (azimut 0) : ses rayons sont des deux côtés du 0°/360°.
        val vs = ViewshedSolver.solveSector(f, sector(0.0), params(3000.0, tilt = 0.0))
        val outline = vs.outline(45.0, 5.0)
        val arc = outline.subList(1, outline.size - 1) // retire les apex (site)
        assertTrue("arc trop court: ${arc.size}", arc.size >= 4)
        // Contour contigu : 1er point d'arc au bord OUEST du nord, dernier au bord EST.
        // (Avec l'ancien tri par relèvement absolu, l'arc démarrait plein nord → saut auto-sécant.)
        assertTrue("premier point à l'ouest du nord", arc.first().longitude < 5.0)
        assertTrue("dernier point à l'est du nord", arc.last().longitude > 5.0)
    }

    // ------------------------------------------------------------ ViewshedSolver

    @Test
    fun flatPlainReachesMaxRadius() {
        val d = distances(50.0, 5000.0)
        val f = field(listOf(0.0, 90.0, 180.0, 270.0), d) { _, _, _ -> 0.0 }
        val vs = ViewshedSolver.solveSector(f, omni(30.0), params(5000.0, tilt = 0.0))
        assertEquals(4, vs.rays.size)
        vs.rays.forEach { assertTrue("ray ${it.bearingDeg} = ${it.maxVisibleM}", it.maxVisibleM >= 4900.0) }
    }

    @Test
    fun hillMasksRayButFlatReferenceReachesMax() {
        val d = distances(50.0, 5000.0)
        // bearing 0 : colline de 200 m vers 1 km ; bearing 180 : plaine.
        val f = field(listOf(0.0, 180.0), d) { b, _, dist ->
            if (b == 0.0 && dist in 1000.0..1100.0) 200.0 else 0.0
        }
        val vs = ViewshedSolver.solveSector(f, omni(30.0), params(5000.0, tilt = 0.0))
        val ray0 = vs.rays.first { it.bearingDeg == 0.0 }
        val ray180 = vs.rays.first { it.bearingDeg == 180.0 }
        assertTrue("masquée par la colline: ${ray0.maxVisibleM}", ray0.maxVisibleM in 900.0..1200.0)
        assertTrue("plaine atteint le max: ${ray180.maxVisibleM}", ray180.maxVisibleM >= 4900.0)
    }

    @Test
    fun curvatureShortensRangeForLowTransmitter() {
        val d = distances(100.0, 15000.0)
        val f = field(listOf(0.0), d, maxRadius = 15000.0) { _, _, _ -> 0.0 }
        val flat = ViewshedSolver.solveSector(f, omni(1.0), params(15000.0, tilt = 0.0, curvature = false))
        val curved = ViewshedSolver.solveSector(f, omni(1.0), params(15000.0, tilt = 0.0, curvature = true))
        val flatR = flat.rays.first().maxVisibleM
        val curvedR = curved.rays.first().maxVisibleM
        assertTrue("sans courbure devrait atteindre le max: $flatR", flatR >= 14900.0)
        assertTrue("la courbure devrait raccourcir: flat=$flatR curved=$curvedR", curvedR < flatR - 2000.0)
    }

    @Test
    fun sectorExcludesBackBearingsOmniDoesNot() {
        val d = distances(100.0, 3000.0)
        val bearings = (0 until 360 step 10).map { it.toDouble() }
        val f = field(bearings, d) { _, _, _ -> 0.0 }
        val sec = ViewshedSolver.solveSector(f, sector(90.0), params(3000.0, tilt = 0.0))
        val om = ViewshedSolver.solveSector(f, omni(30.0), params(3000.0, tilt = 0.0))
        assertEquals(bearings.size, om.rays.size)
        assertTrue("axe 90 présent", sec.rays.any { it.bearingDeg == 90.0 })
        assertFalse("arrière 270 absent", sec.rays.any { it.bearingDeg == 270.0 })
        sec.rays.forEach { assertTrue(CoverageGeo.angularDifferenceDeg(it.bearingDeg, 90.0) <= 75.0) }
    }

    @Test
    fun strongerDownTiltShortensRange() {
        val d = distances(50.0, 5000.0)
        val f = field(listOf(0.0), d) { _, _, _ -> 0.0 }
        val t0 = ViewshedSolver.solveSector(f, omni(30.0), params(5000.0, tilt = 0.0)).rays.first().maxVisibleM
        val t5 = ViewshedSolver.solveSector(f, omni(30.0), params(5000.0, tilt = 5.0)).rays.first().maxVisibleM
        val t12 = ViewshedSolver.solveSector(f, omni(30.0), params(5000.0, tilt = 12.0)).rays.first().maxVisibleM
        assertTrue("tilt 0 atteint le max: $t0", t0 >= 4900.0)
        assertTrue("tilt <= ½ ouverture = portée max: $t5", t5 >= 4900.0)
        assertTrue("tilt fort raccourcit nettement: $t12", t12 < 1200.0)
        assertTrue("monotone: $t12 <= $t5", t12 <= t5)
    }

    // --------------------------------------------------- TerrainFieldLoader + engine

    @Test
    fun loaderBuildsFieldAndAppliesObstacles() = runBlocking {
        // Bâtiment réaliste couvrant tout le disque de 1 km autour du site (exerce le chemin « grille »).
        val building = object : BuildingObstacle {
            override val minLon = 4.95
            override val minLat = 44.95
            override val maxLon = 5.05
            override val maxLat = 45.05
            override fun contains(longitude: Double, latitude: Double) = true
            override fun topAltitude(terrainElevation: Double) = terrainElevation + 50.0
        }
        val loader = TerrainFieldLoader(
            getElevations = { pts -> List(pts.size) { 0.0 } },
            fetchBuildings = { _, _, _, _ -> listOf(building) },
            maxPointsPerRequest = 50
        )
        val field = loader.load(45.0, 5.0, maxRadiusM = 1000.0, angularStepDeg = 90.0, sampleStepM = 100.0, includeObstacles = true)
        assertEquals(4, field.rays.size) // 0, 90, 180, 270
        assertEquals(10, field.rays[0].distances.size) // 100..1000 par pas de 100
        assertTrue(field.obstaclesIncluded)
        assertEquals(50.0, field.rays[0].ground[0], 1e-6) // sol surélevé par le toit
    }

    @Test
    fun engineUnionTwoOppositeAntennas() = runBlocking {
        val loader = TerrainFieldLoader(
            getElevations = { pts -> List(pts.size) { 0.0 } },
            fetchBuildings = { _, _, _, _ -> emptyList() }
        )
        val engine = CoverageEngine(loader) { 123L }
        val site = ResolvedSite("X", 45.0, 5.0, "Orange", listOf(sector(0.0), sector(180.0)))
        val req = CoverageRequest(3000.0, 10.0, 100.0, includeObstacles = false, viewshed = params(3000.0, tilt = 0.0))
        val cov = engine.compute(site, req)
        assertEquals(2, cov.sectors.size)
        assertEquals(123L, cov.computedAtMillis)
        val s0 = cov.sectors[0]
        val s180 = cov.sectors[1]
        assertTrue("antenne az 0 couvre l'avant", s0.rays.any { it.bearingDeg == 0.0 })
        assertTrue("antenne az 180 couvre l'arrière", s180.rays.any { it.bearingDeg == 180.0 })
        assertFalse("az 0 ne couvre pas 180", s0.rays.any { it.bearingDeg == 180.0 })
        assertTrue(s0.outline(site.latitude, site.longitude).isNotEmpty())
        assertTrue(s180.outline(site.latitude, site.longitude).isNotEmpty())
    }

    @Test
    fun emptyAntennasYieldEmptyCoverage() = runBlocking {
        val loader = TerrainFieldLoader(
            getElevations = { pts -> List(pts.size) { 0.0 } },
            fetchBuildings = { _, _, _, _ -> emptyList() }
        )
        val engine = CoverageEngine(loader) { 0L }
        val cov = engine.compute(
            ResolvedSite("X", 45.0, 5.0, null, emptyList()),
            CoverageRequest(1000.0, 30.0, 100.0, includeObstacles = false, viewshed = params(1000.0))
        )
        assertTrue(cov.isEmpty)
    }

    // ------------------------------------------------------- SiteEmitterResolver

    @Test
    fun resolverFiltersFhAndDetectsOmni() {
        val ants = listOf(
            AntenneDbEntity("a1", "X", "S", 1, 30, 25.0, 0),    // sectorielle
            AntenneDbEntity("a2", "X", "S", 2, null, 25.0, 0),  // omni sans azimut -> 0°
            AntenneDbEntity("fh", "X", "S", 3, 100, 25.0, 1),   // faisceau hertzien -> exclu
            AntenneDbEntity("a3", "X", "S", 4, 90, null, 0)     // sans hauteur -> repli (15 m)
        )
        val labels = mapOf(
            1 to "Antenne directive",
            2 to "Antenne omnidirectionnelle",
            3 to "Antenne à faisceau",
            4 to "Panneau"
        )
        val specs = SiteEmitterResolver.resolve(ants, labels, "Orange", 800, ViewshedParams(maxRadiusM = 5000.0))
        // a1 (sect) + a2 (omni) + a3 (sect, hauteur repliée) ; le faisceau hertzien (fh) est exclu.
        assertEquals(3, specs.size)
        val a2 = specs.first { it.aerId == "a2" }
        assertTrue("a2 détectée omni", a2.omni)
        assertEquals(0.0, a2.azimutDeg, 1e-9)
        val a1 = specs.first { it.aerId == "a1" }
        assertFalse("a1 sectorielle", a1.omni)
        assertEquals(800, a1.frequencyMHz)
        assertEquals("Orange", a1.operator)
        val a3 = specs.first { it.aerId == "a3" }
        assertEquals("a3 hauteur repliée à 15 m", 15.0, a3.txHeightM, 1e-9)
        assertEquals(90.0, a3.azimutDeg, 1e-9)
    }
}
