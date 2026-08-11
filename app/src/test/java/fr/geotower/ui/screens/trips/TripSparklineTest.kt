package fr.geotower.ui.screens.trips

import fr.geotower.data.trip.TripGeometryCodec
import fr.geotower.data.trip.leg
import fr.geotower.data.trip.plan
import fr.geotower.data.trip.step
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSparklineTest {
    @Test
    fun keepsEveryPointInsideTheBox() {
        val points = tripShapePoints(plan(listOf(step(48.80, 2.30), step(48.95, 2.55), step(48.70, 2.20))))

        assertTrue(points.isNotEmpty())
        points.forEach {
            assertTrue("x=${it[0]}", it[0] in -0.001f..1.001f)
            assertTrue("y=${it[1]}", it[1] in -0.001f..1.001f)
        }
    }

    @Test
    fun putsNorthAtTheTop() {
        val points = tripShapePoints(plan(listOf(step(48.70, 2.30), step(48.90, 2.30))))

        // Première étape au sud, donc plus bas à l'écran que la seconde.
        assertTrue("${points.first()[1]} > ${points.last()[1]}", points.first()[1] > points.last()[1])
    }

    @Test
    fun doesNotStretchAStraightLineAcrossTheBox() {
        // Une tournée strictement nord-sud : elle doit rester une ligne verticale, pas s'étaler.
        val points = tripShapePoints(plan(listOf(step(48.70, 2.30), step(48.90, 2.30))))

        assertEquals(points.first()[0], points.last()[0], 1e-4f)
        assertEquals(0.5f, points.first()[0], 1e-4f)
        // Et elle occupe toute la hauteur disponible.
        assertTrue(abs(points.first()[1] - points.last()[1]) > 0.99f)
    }

    @Test
    fun compensatesForLongitudeShrinkingWithLatitude() {
        // Même écart en degrés dans les deux axes : à 48° de latitude, l'étendue est-ouest est plus
        // courte sur le terrain, la miniature doit donc être moins large que haute.
        val points = tripShapePoints(plan(listOf(step(48.70, 2.30), step(48.90, 2.50))))

        val width = abs(points.first()[0] - points.last()[0])
        val height = abs(points.first()[1] - points.last()[1])
        assertTrue("largeur=$width hauteur=$height", width < height)
    }

    @Test
    fun followsTheComputedRouteRatherThanTheStraightLine() {
        val detour = listOf(
            doubleArrayOf(48.80, 2.30),
            doubleArrayOf(48.95, 2.30),
            doubleArrayOf(48.80, 2.40)
        )
        val routed = plan(
            listOf(step(48.80, 2.30), step(48.80, 2.40)),
            legs = listOf(leg(0, 1).copy(encodedGeometry = TripGeometryCodec.encode(detour)))
        )

        val points = tripShapePoints(routed)

        // Trois points du détour, pas deux extrémités : la miniature montre la route.
        assertEquals(3, points.size)
    }

    @Test
    fun subsamplesVeryLongRoutes() {
        val long = List(5_000) { doubleArrayOf(48.80 + it * 0.0001, 2.30 + it * 0.0001) }
        val routed = plan(
            listOf(step(48.80, 2.30), step(49.30, 2.80)),
            legs = listOf(leg(0, 1).copy(encodedGeometry = TripGeometryCodec.encode(long)))
        )

        val points = tripShapePoints(routed)

        assertTrue("${points.size} points", points.size in 2..260)
    }

    @Test
    fun rendersNothingBelowTwoPoints() {
        assertEquals(emptyList<FloatArray>(), tripShapePoints(plan(listOf(step(48.80, 2.30)))))
        assertEquals(emptyList<FloatArray>(), tripShapePoints(plan(emptyList())))
    }
}
