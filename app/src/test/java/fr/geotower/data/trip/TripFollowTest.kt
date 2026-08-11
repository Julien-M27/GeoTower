package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFollowTest {
    private fun routedLeg(fromIndex: Int, toIndex: Int, points: List<DoubleArray>, meters: Double) =
        leg(fromIndex, toIndex, distanceMeters = meters)
            .copy(encodedGeometry = TripGeometryCodec.encode(points))

    @Test
    fun measuresTheDistanceToASegmentAlongItsLength() {
        // Un point à mi-longueur d'un segment est-ouest, décalé de ~111 m vers le nord.
        val distance = distanceToSegmentMeters(
            latitude = 48.801,
            longitude = 2.35,
            fromLatitude = 48.800,
            fromLongitude = 2.30,
            toLatitude = 48.800,
            toLongitude = 2.40
        )

        assertEquals(110.5, distance, 2.0)
    }

    @Test
    fun clampsToTheSegmentEndsRatherThanItsInfiniteLine() {
        // Point très à l'ouest du segment : la distance est celle de son extrémité, pas celle de
        // la droite qui le prolonge.
        val distance = distanceToSegmentMeters(
            latitude = 48.800,
            longitude = 2.20,
            fromLatitude = 48.800,
            fromLongitude = 2.30,
            toLatitude = 48.800,
            toLongitude = 2.40
        )

        val toEnd = haversineMeters(48.800, 2.20, 48.800, 2.30)
        assertEquals(toEnd, distance, 20.0)
    }

    @Test
    fun handlesADegenerateSegment() {
        val distance = distanceToSegmentMeters(48.801, 2.30, 48.800, 2.30, 48.800, 2.30)

        assertEquals(haversineMeters(48.801, 2.30, 48.800, 2.30), distance, 2.0)
    }

    @Test
    fun pointsAtTheFirstUnvisitedStepInTourOrder() {
        val tour = plan(
            listOf(
                step(48.80, 2.30, visitedAtMillis = 1L),
                step(48.85, 2.35),
                step(48.90, 2.40)
            )
        )

        val status = computeTripFollowStatus(tour, latitude = 48.86, longitude = 2.36)

        // La 3e est plus loin, mais c'est la 2e qui vient : l'ordre de la tournée fait foi.
        assertEquals(1, status.nextStepIndex)
    }

    @Test
    fun reportsTheTourFinishedWhenEveryStepIsDone() {
        val done = plan(listOf(step(48.80, 2.30, visitedAtMillis = 1L), step(48.85, 2.35, visitedAtMillis = 2L)))

        val status = computeTripFollowStatus(done, latitude = 48.86, longitude = 2.36)

        assertNull(status.nextStepIndex)
        assertNull(status.distanceToNextMeters)
        assertEquals(0.0, status.remainingDistanceMeters, 0.01)
    }

    @Test
    fun checksOffEveryUnvisitedStepWithinReach() {
        val tour = plan(
            listOf(
                step(48.8000, 2.3000),
                step(48.8003, 2.3000), // ~33 m : dans le rayon
                step(48.9000, 2.3000)  // loin
            )
        )

        val status = computeTripFollowStatus(tour, latitude = 48.8000, longitude = 2.3000)

        // Passer près d'une étape la relève, même si ce n'est pas celle qu'on visait.
        assertEquals(listOf(0, 1), status.reachedStepIndices)
    }

    @Test
    fun neverChecksOffAStepTwice() {
        val tour = plan(listOf(step(48.80, 2.30, visitedAtMillis = 42L), step(48.90, 2.40)))

        val status = computeTripFollowStatus(tour, latitude = 48.80, longitude = 2.30)

        assertEquals(emptyList<Int>(), status.reachedStepIndices)
    }

    @Test
    fun staysSilentAboutTheRouteWhenNoLegIsComputed() {
        val tour = plan(listOf(step(48.80, 2.30), step(48.90, 2.40)))

        val status = computeTripFollowStatus(tour, latitude = 47.0, longitude = 1.0)

        // Sans route connue, on ne peut pas prétendre s'en écarter.
        assertNull(status.offRouteMeters)
        assertTrue(!status.isOffRoute)
    }

    @Test
    fun warnsOnlyBeyondTheThreshold() {
        val route = listOf(doubleArrayOf(48.800, 2.300), doubleArrayOf(48.800, 2.400))
        val tour = plan(
            listOf(step(48.800, 2.300), step(48.800, 2.400)),
            legs = listOf(routedLeg(0, 1, route, meters = 7_300.0))
        )

        // ~55 m au nord du tracé : on ne dit rien.
        val near = computeTripFollowStatus(tour, latitude = 48.8005, longitude = 2.350)
        // ~550 m au nord : on prévient.
        val far = computeTripFollowStatus(tour, latitude = 48.805, longitude = 2.350)

        assertTrue("écart=${near.offRouteMeters}", !near.isOffRoute)
        assertTrue("écart=${far.offRouteMeters}", far.isOffRoute)
    }

    @Test
    fun countsTheLegsLeftAfterTheNextStep() {
        val tour = plan(
            listOf(step(48.800, 2.300, visitedAtMillis = 1L), step(48.800, 2.400), step(48.800, 2.500)),
            legs = listOf(
                leg(0, 1, distanceMeters = 7_300.0),
                leg(1, 2, distanceMeters = 7_300.0)
            )
        )

        val status = computeTripFollowStatus(tour, latitude = 48.800, longitude = 2.390)

        // Distance à l'étape 2 (~730 m) + le segment 1→2 qui suit. Le segment 0→1 est derrière nous.
        assertEquals(730.0 + 7_300.0, status.remainingDistanceMeters, 60.0)
    }
}
