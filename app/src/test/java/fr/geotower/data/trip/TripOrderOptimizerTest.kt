package fr.geotower.data.trip

import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripOrderOptimizerTest {
    @Test
    fun keepsTheFirstStepInPlace() {
        val steps = shuffledRing(count = 8, seed = 7)
        val outcome = TripOrderOptimizer.optimize(plan(steps, returnToStart = true))

        assertEquals(steps.first().label, outcome.plan.steps.first().label)
        assertEquals(steps.size, outcome.plan.steps.size)
        assertEquals(steps.map { it.label }.toSet(), outcome.plan.steps.map { it.label }.toSet())
    }

    @Test
    fun untanglesACrossedLoop() {
        // Les quatre coins d'un rectangle, donnés en croix. Sur des points en position convexe, le
        // meilleur circuit est le périmètre : c'est vérifiable à la main.
        val corners = listOf(
            step(48.80, 2.30, label = "sud-ouest"),
            step(48.90, 2.40, label = "nord-est"),
            step(48.80, 2.40, label = "sud-est"),
            step(48.90, 2.30, label = "nord-ouest")
        )

        val outcome = TripOrderOptimizer.optimize(plan(corners, returnToStart = true))

        assertEquals(
            listOf("sud-ouest", "sud-est", "nord-est", "nord-ouest"),
            outcome.plan.steps.map { it.label }
        )
        assertTrue(outcome.changed)
    }

    @Test
    fun matchesBruteForceOnSmallLoops() {
        val steps = shuffledRing(count = 6, seed = 3)
        val matrix = TripOrderOptimizer.distances(steps)

        val outcome = TripOrderOptimizer.optimize(plan(steps, returnToStart = true))
        val achieved = outcome.straightAfterMeters
        val best = bestCycleLength(matrix)

        assertEquals(best, achieved, 1.0)
    }

    @Test
    fun neverLengthensTheTour() {
        for (seed in 1..25) {
            val steps = randomSteps(count = 12, seed = seed)
            for (loop in listOf(false, true)) {
                val outcome = TripOrderOptimizer.optimize(plan(steps, returnToStart = loop))
                assertTrue(
                    "graine=$seed boucle=$loop avant=${outcome.straightBeforeMeters} après=${outcome.straightAfterMeters}",
                    outcome.straightAfterMeters <= outcome.straightBeforeMeters + 1e-6
                )
            }
        }
    }

    @Test
    fun dropsStaleLegsWhenItReorders() {
        val steps = shuffledRing(count = 8, seed = 11)
        val stale = listOf(leg(0, 1), leg(1, 2))

        val outcome = TripOrderOptimizer.optimize(plan(steps, legs = stale, returnToStart = true))

        assertTrue(outcome.changed)
        // Les segments reliaient d'autres couples d'étapes : les garder afficherait un tracé faux.
        assertEquals(emptyList<TripLeg>(), outcome.plan.legs)
    }

    @Test
    fun leavesTripsTooShortToReorderUntouched() {
        val two = plan(ladder(2))
        val outcome = TripOrderOptimizer.optimize(two)

        assertEquals(two.steps, outcome.plan.steps)
        assertTrue(!outcome.changed)
    }

    @Test
    fun leavesOversizedTripsUntouched() {
        val huge = plan(randomSteps(count = TripOrderOptimizer.MAX_OPTIMIZABLE_STEPS + 1, seed = 2))
        val outcome = TripOrderOptimizer.optimize(huge)

        assertEquals(huge.steps, outcome.plan.steps)
        assertTrue(!outcome.changed)
    }

    /** Points régulièrement répartis sur un cercle, donnés dans le désordre. */
    private fun shuffledRing(count: Int, seed: Int): List<TripStep> {
        val ring = List(count) { index ->
            val angle = 2 * Math.PI * index / count
            step(48.85 + 0.2 * sin(angle), 2.35 + 0.3 * cos(angle), label = "point $index")
        }
        val shuffled = ring.drop(1).shuffled(Random(seed.toLong()))
        return listOf(ring.first()) + shuffled
    }

    private fun randomSteps(count: Int, seed: Int): List<TripStep> {
        val random = Random(seed.toLong())
        return List(count) {
            step(48.0 + random.nextDouble(), 1.0 + random.nextDouble() * 3.0, label = "point $it")
        }
    }

    /** Meilleur circuit possible, première étape figée : praticable jusqu'à 7 ou 8 étapes. */
    private fun bestCycleLength(matrix: Array<DoubleArray>): Double {
        var best = Double.MAX_VALUE
        permutations((1 until matrix.size).toList()) { tail ->
            val order = listOf(0) + tail
            val length = TripOrderOptimizer.straightLength(order, matrix, cycle = true)
            if (length < best) best = length
        }
        return best
    }

    private fun permutations(items: List<Int>, action: (List<Int>) -> Unit) {
        if (items.size <= 1) {
            action(items)
            return
        }
        for (index in items.indices) {
            val head = items[index]
            permutations(items.filterIndexed { position, _ -> position != index }) { rest ->
                action(listOf(head) + rest)
            }
        }
    }
}
