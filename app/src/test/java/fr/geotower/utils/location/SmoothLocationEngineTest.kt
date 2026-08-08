package fr.geotower.utils.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SmoothLocationEngineTest {

    private val baseLat = 48.8584
    private val baseLon = 2.2945

    /** Distance approchée en mètres entre deux couples de coordonnées, autour de la position de test. */
    private fun distanceM(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double
    ): Double {
        val metersPerDegLon = SmoothLocationEngine.metersPerDegreeLongitude(fromLat)
        val east = (toLon - fromLon) * metersPerDegLon
        val north = (toLat - fromLat) * SmoothLocationEngine.METERS_PER_DEG_LAT
        return hypot(east, north)
    }

    private fun offsetNorth(meters: Double) = baseLat + meters / SmoothLocationEngine.METERS_PER_DEG_LAT

    @Test
    fun `sans relevé le moteur ne propose aucune position`() {
        val engine = SmoothLocationEngine()

        assertNull(engine.sample(0L))
        assertFalse(engine.hasFix)
        assertTrue(engine.isIdle(0L))
    }

    @Test
    fun `le premier relevé se pose exactement où il est annoncé`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L))

        val position = engine.sample(1_000L)
        assertNotNull(position)
        assertEquals(baseLat, position!!.latitude, 1e-9)
        assertEquals(baseLon, position.longitude, 1e-9)
    }

    @Test
    fun `à l'arrêt le repère ne dérive pas`() {
        val engine = SmoothLocationEngine()
        // Vitesse sous le seuil : c'est du bruit GPS, surtout ne pas extrapoler dessus.
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L, speedMps = 0.2f, bearingDeg = 90f))

        val afterOneSecond = engine.sample(2_000L)!!
        assertEquals(0.0, distanceM(baseLat, baseLon, afterOneSecond.latitude, afterOneSecond.longitude), 0.01)
        assertTrue(engine.isIdle(2_000L))
    }

    @Test
    fun `entre deux relevés le repère avance à la vitesse annoncée`() {
        val engine = SmoothLocationEngine()
        // 10 m/s plein est.
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L, speedMps = 10f, bearingDeg = 90f))

        val half = engine.sample(1_500L)!!
        val full = engine.sample(2_000L)!!

        assertEquals(5.0, distanceM(baseLat, baseLon, half.latitude, half.longitude), 0.1)
        assertEquals(10.0, distanceM(baseLat, baseLon, full.latitude, full.longitude), 0.1)
        // Plein est : la latitude ne bouge pas.
        assertEquals(baseLat, full.latitude, 1e-9)
        assertTrue(full.longitude > baseLon)
    }

    @Test
    fun `l'extrapolation s'arrête au bout de l'horizon`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L, speedMps = 10f, bearingDeg = 90f))

        val horizonMs = (SmoothLocationEngine.MAX_EXTRAPOLATION_S * 1000).toLong()
        val atHorizon = engine.sample(1_000L + horizonMs)!!
        val wellAfter = engine.sample(1_000L + horizonMs + 5_000L)!!

        assertEquals(
            "Le repère doit se figer plutôt que partir à l'aveugle",
            0.0,
            distanceM(atHorizon.latitude, atHorizon.longitude, wellAfter.latitude, wellAfter.longitude),
            0.01
        )
        assertTrue(engine.isIdle(1_000L + horizonMs + 5_000L))
    }

    @Test
    fun `une correction de quelques mètres se résorbe en glissant`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L, speedMps = 0f))
        // Deuxième relevé 10 m au nord alors que le GPS annonce l'arrêt : c'est une correction de
        // position, pas un déplacement — le repère doit y glisser sans extrapoler quoi que ce soit.
        val correctedLat = offsetNorth(10.0)
        engine.onFix(RawFix(correctedLat, baseLon, timeMs = 2_000L, speedMps = 0f))

        val immediately = engine.sample(2_000L)!!
        val shortly = engine.sample(2_300L)!!
        val later = engine.sample(3_500L)!!

        // À l'instant du relevé le repère est encore à son ancienne place : pas de téléportation.
        assertEquals(10.0, distanceM(correctedLat, baseLon, immediately.latitude, immediately.longitude), 0.1)
        // Puis il se rapproche, sans jamais dépasser.
        val remainingShortly = distanceM(correctedLat, baseLon, shortly.latitude, shortly.longitude)
        val remainingLater = distanceM(correctedLat, baseLon, later.latitude, later.longitude)
        assertTrue(remainingShortly in 0.1..9.9)
        assertTrue(remainingLater < remainingShortly)
        // Constante de temps de 0,45 s : au bout d'1,5 s il ne reste qu'une poignée de centimètres,
        // et le relevé suivant absorbera ce reliquat.
        assertTrue("Reliquat trop important : $remainingLater m", remainingLater < 0.5)
    }

    @Test
    fun `un vrai saut de position est appliqué sèchement`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L))
        // 500 m d'un coup : sortie de tunnel ou premier point fiable, pas une correction.
        val jumpedLat = offsetNorth(500.0)
        engine.onFix(RawFix(jumpedLat, baseLon, timeMs = 2_000L))

        val immediately = engine.sample(2_000L)!!
        assertEquals(
            "Glisser sur 500 m serait absurde",
            0.0,
            distanceM(jumpedLat, baseLon, immediately.latitude, immediately.longitude),
            0.01
        )
    }

    @Test
    fun `sans vitesse annoncée elle est reconstruite depuis le relevé précédent`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L))
        // 8 m parcourus vers le nord en 1 s, sans champ vitesse : 8 m/s doivent être déduits.
        val secondLat = offsetNorth(8.0)
        engine.onFix(RawFix(secondLat, baseLon, timeMs = 2_000L))

        // Mesuré au bout de l'horizon, une fois le rattrapage du premier point résorbé : 8 m/s
        // reconstruits pendant 1,6 s.
        val horizonMs = (SmoothLocationEngine.MAX_EXTRAPOLATION_S * 1000).toLong()
        val atHorizon = engine.sample(2_000L + horizonMs)!!
        val travelled = distanceM(secondLat, baseLon, atHorizon.latitude, atHorizon.longitude)
        assertEquals(8.0 * SmoothLocationEngine.MAX_EXTRAPOLATION_S, travelled, 0.5)
        assertTrue(atHorizon.latitude > secondLat)
    }

    @Test
    fun `remettre à zéro efface l'ancre`() {
        val engine = SmoothLocationEngine()
        engine.onFix(RawFix(baseLat, baseLon, timeMs = 1_000L))
        engine.reset()

        assertNull(engine.sample(1_000L))
        assertFalse(engine.hasFix)
    }
}
