package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlanTest {
    @Test
    fun addsUpWhatTheWholeTourProduced() {
        val tour = plan(
            listOf(
                step(48.80, 2.30).copy(photosSentCount = 3),
                step(48.81, 2.31),
                step(48.82, 2.32).copy(photosSentCount = 2)
            )
        )

        assertEquals(5, tour.photosSentTotal())
        assertEquals(0, plan(ladder(3)).photosSentTotal())
    }

    @Test
    fun refusesANegativePhotoCount() {
        // Fichier trafiqué ou corrompu : un compteur négatif fausserait le total de la tournée.
        val step = step(48.80, 2.30).copy(photosSentCount = -4).sanitized()!!

        assertEquals(0, step.photosSentCount)
    }

    @Test
    fun listsTheLegsAnOpenTripNeeds() {
        assertEquals(listOf(0 to 1, 1 to 2), plan(ladder(3)).legPairs())
    }

    @Test
    fun closesTheLoopOnTheFirstStep() {
        assertEquals(listOf(0 to 1, 1 to 2, 2 to 0), plan(ladder(3), returnToStart = true).legPairs())
    }

    @Test
    fun needsNoLegBelowTwoSteps() {
        assertEquals(emptyList<Pair<Int, Int>>(), plan(ladder(1), returnToStart = true).legPairs())
    }

    @Test
    fun onlyCountsTheLegsItActuallyKnows() {
        val partial = plan(ladder(3), legs = listOf(leg(0, 1, distanceMeters = 2_500.0)))

        assertEquals(2_500.0, partial.totalDistanceMeters(), 0.01)
        assertTrue(!partial.isRouteComplete())
    }

    @Test
    fun addsStopTimeForEveryStepButTheDeparture() {
        val tour = plan(ladder(4), legs = listOf(leg(0, 1), leg(1, 2), leg(2, 3)))
            .copy(stopDurationMinutes = 10)

        // Trois segments à 60 s, plus trois arrêts de 10 min : le départ n'est pas un arrêt.
        assertEquals(180.0 + 3 * 600.0, tour.totalDurationWithStopsSeconds(), 0.01)
    }

    @Test
    fun dropsStepsWithImpossibleCoordinates() {
        val broken = plan(listOf(step(48.85, 2.35), step(Double.NaN, 2.35), step(91.0, 2.35)))

        assertEquals(1, broken.sanitized()?.steps?.size)
    }

    @Test
    fun dropsLegsThatNoLongerPointAtAStep() {
        val orphaned = plan(ladder(2), legs = listOf(leg(0, 1), leg(1, 5), leg(2, 3)))

        assertEquals(listOf(0 to 1), orphaned.sanitized()?.legs?.map { it.fromIndex to it.toIndex })
    }

    @Test
    fun normalisesStatusProfileAndReminders() {
        val odd = plan(ladder(2), profile = "helicopter").copy(
            status = "en cours",
            stopDurationMinutes = -5,
            reminderOffsetsMinutes = listOf(180, 1440, 180, 0, -10)
        )

        val clean = odd.sanitized()!!
        assertEquals(RouteApi.PROFILE_CAR, clean.profile)
        assertEquals(TripPlan.STATUS_DRAFT, clean.status)
        assertEquals(0, clean.stopDurationMinutes)
        assertEquals(listOf(1440, 180), clean.reminderOffsetsMinutes)
    }

    @Test
    fun keepsThePedestrianProfile() {
        val walked = plan(ladder(2), profile = RouteApi.PROFILE_PEDESTRIAN)

        assertEquals(RouteApi.PROFILE_PEDESTRIAN, walked.sanitized()?.profile)
    }

    @Test
    fun forgetsProgressAndScheduleWhenDuplicating() {
        val done = plan(listOf(step(48.85, 2.35, visitedAtMillis = 42L), step(48.86, 2.36)))
            .copy(plannedAtMillis = 1_000L, reminderOffsetsMinutes = listOf(1440), status = TripPlan.STATUS_DONE)

        val copy = TripPlanStore.duplicated(done, name = "Tournée (copie)", createdAtMillis = 5L)

        assertTrue(copy.id != done.id)
        assertNull(copy.plannedAtMillis)
        assertEquals(emptyList<Int>(), copy.reminderOffsetsMinutes)
        assertEquals(TripPlan.STATUS_DRAFT, copy.status)
        assertEquals(0, copy.visitedCount())
        assertEquals(done.steps.size, copy.steps.size)
    }

    @Test
    fun dropsTheRouteWhenReversingTheDirection() {
        val tour = plan(ladder(3), legs = listOf(leg(0, 1), leg(1, 2)))

        val back = TripPlanStore.reversed(tour)

        // Les sens uniques ne se parcourent pas à l'envers : le tracé calculé ne vaut plus rien.
        assertEquals(emptyList<TripLeg>(), back.legs)
        assertEquals(tour.steps.reversed().map { it.label }, back.steps.map { it.label })
    }
}
