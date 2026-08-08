package fr.geotower.utils.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin

class PedestrianDeadReckoningTest {

    private val sampleIntervalMs = 20L // 50 Hz, cadence de SENSOR_DELAY_GAME
    private val cadenceHz = 1.8
    private val gravity = 9.81

    /**
     * Simule une marche : la pesanteur plus une oscillation verticale à la cadence du pas. C'est la
     * forme d'onde que voit réellement l'accéléromètre dans une poche ou une main.
     */
    private fun walk(
        target: PedestrianDeadReckoning,
        durationMs: Long,
        startMs: Long = 0L,
        amplitude: Double = 2.0
    ): Long {
        var time = startMs
        while (time < startMs + durationMs) {
            val phase = 2 * PI * cadenceHz * (time / 1000.0)
            target.onAccelerometerSample(time, (gravity + amplitude * sin(phase)).toFloat(), 0f, 0f)
            time += sampleIntervalMs
        }
        return time
    }

    @Test
    fun `la marche fait avancer le repère dans la direction du cap`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f) // plein nord

        val end = walk(reckoning, durationMs = 5_000L)
        val displacement = reckoning.displacementSince(0L, end)

        // ~9 pas en 5 s à 1,8 Hz, autour de 70 cm chacun.
        val distance = hypot(displacement.east, displacement.north)
        assertTrue("Aucun pas détecté sur 5 s de marche", distance > 3.0)
        assertTrue("Dérive invraisemblable : $distance m", distance < 12.0)
        assertTrue("Le déplacement doit suivre le cap (nord)", displacement.north > 0.0)
        assertTrue(abs(displacement.east) < 0.01)
    }

    @Test
    fun `le cap oriente bien le déplacement`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(90f) // plein est

        val end = walk(reckoning, durationMs = 5_000L)
        val displacement = reckoning.displacementSince(0L, end)

        assertTrue(displacement.east > 3.0)
        assertTrue(abs(displacement.north) < 0.01)
    }

    @Test
    fun `en voiture les vibrations ne sont pas comptées comme des pas`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f)
        // 50 km/h : on est clairement au-dessus du régime piéton.
        reckoning.onGpsFix(
            timeMs = 0L,
            speedMps = 14f,
            previous = null,
            current = RawFix(48.0, 2.0, timeMs = 0L)
        )

        val end = walk(reckoning, durationMs = 5_000L)
        val displacement = reckoning.displacementSince(0L, end)

        assertEquals(0.0, displacement.east, 0.0)
        assertEquals(0.0, displacement.north, 0.0)
    }

    @Test
    fun `sans cap connu aucun déplacement n'est enregistré`() {
        val reckoning = PedestrianDeadReckoning()
        // On ne fournit volontairement pas setHeading : impossible d'orienter les pas.

        val end = walk(reckoning, durationMs = 5_000L)
        val displacement = reckoning.displacementSince(0L, end)

        assertEquals(0.0, hypot(displacement.east, displacement.north), 0.0)
    }

    @Test
    fun `un appareil immobile ne produit aucun pas`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f)

        // Bruit résiduel de repos, très en dessous du seuil de détection.
        val end = walk(reckoning, durationMs = 5_000L, amplitude = 0.05)
        val displacement = reckoning.displacementSince(0L, end)

        assertEquals(0.0, hypot(displacement.east, displacement.north), 0.0)
        assertFalse(reckoning.hasPendingMotion(end))
    }

    @Test
    fun `chaque pas est étalé au lieu d'être ajouté d'un bloc`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f)

        // On marche jusqu'à avoir un pas fraîchement détecté, pour ne pas dépendre de l'endroit où
        // la simulation s'arrête dans le cycle de la foulée.
        var now = walk(reckoning, durationMs = 3_000L)
        while (!reckoning.hasPendingMotion(now) && now < 10_000L) {
            now = walk(reckoning, durationMs = 100L, startMs = now)
        }
        assertTrue("Aucun pas récent à observer", reckoning.hasPendingMotion(now))

        // Ce pas continue de se déployer, mais par petits incréments : c'est ce qui évite au repère
        // d'avancer par à-coups de 70 cm.
        var previous = reckoning.displacementSince(0L, now).north
        var biggestIncrement = 0.0
        var time = now + 20L
        while (time <= now + 400L) {
            val current = reckoning.displacementSince(0L, time).north
            biggestIncrement = maxOf(biggestIncrement, current - previous)
            previous = current
            time += 20L
        }

        assertTrue("Le pas en cours doit continuer d'avancer", biggestIncrement > 0.0)
        assertTrue("Avancée par à-coups : $biggestIncrement m d'un coup", biggestIncrement < 0.1)
        assertTrue(previous > reckoning.displacementSince(0L, now).north)
        assertFalse(reckoning.hasPendingMotion(now + 500L))
    }

    @Test
    fun `le déplacement à l'estime est plafonné`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f)

        // Deux minutes de marche sans le moindre relevé GPS : bien au-delà de ce que l'estime peut
        // tenir, le cumul doit être borné.
        val end = walk(reckoning, durationMs = 120_000L)
        val displacement = reckoning.displacementSince(0L, end)

        assertTrue(hypot(displacement.east, displacement.north) <= 25.0 + 1e-6)
    }

    @Test
    fun `remettre à zéro oublie les pas passés`() {
        val reckoning = PedestrianDeadReckoning()
        reckoning.setHeading(0f)
        val end = walk(reckoning, durationMs = 5_000L)
        assertTrue(reckoning.displacementSince(0L, end).north > 0.0)

        reckoning.reset()

        assertEquals(0.0, reckoning.displacementSince(0L, end).north, 0.0)
    }
}
