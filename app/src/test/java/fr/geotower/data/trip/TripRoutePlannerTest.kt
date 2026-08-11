package fr.geotower.data.trip

import fr.geotower.data.api.RouteApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRoutePlannerTest {
    @Test
    fun buildsTheSequenceOfVisitedSteps() {
        assertEquals(listOf(0, 1, 2, 3), TripRoutePlanner.routeSequence(stepCount = 4, returnToStart = false))
        assertEquals(emptyList<Int>(), TripRoutePlanner.routeSequence(stepCount = 1, returnToStart = true))
    }

    @Test
    fun repeatsTheFirstStepWhenTheTripLoops() {
        assertEquals(listOf(0, 1, 2, 0), TripRoutePlanner.routeSequence(stepCount = 3, returnToStart = true))
    }

    @Test
    fun sendsASmallTripInASingleRequest() {
        assertEquals(listOf(listOf(0, 1, 2)), TripRoutePlanner.planRequests(plan(ladder(3))))
    }

    @Test
    fun fillsARequestUpToTheServiceCeiling() {
        val requests = TripRoutePlanner.planRequests(plan(ladder(RouteApi.MAX_POINTS_PER_ROUTE_REQUEST)))

        assertEquals(1, requests.size)
        assertEquals(RouteApi.MAX_POINTS_PER_ROUTE_REQUEST, requests.single().size)
        assertEquals(RouteApi.MAX_INTERMEDIATES, requests.single().size - 2)
    }

    @Test
    fun splitsLongTripsIntoOverlappingChunks() {
        val requests = TripRoutePlanner.planRequests(plan(ladder(20)))

        assertEquals(2, requests.size)
        assertTrue(requests.all { it.size <= RouteApi.MAX_POINTS_PER_ROUTE_REQUEST })
        // Le recouvrement d'un point est la raison d'être de la découpe : sans lui, le segment entre
        // les deux tranches ne serait calculé par personne.
        assertEquals(requests[0].last(), requests[1].first())
        assertEquals((0..19).toList(), requests.flatten().distinct())
    }

    @Test
    fun loopsFitOneFewerDistinctStepPerRequest() {
        // Le retour au départ ajoute un point à la suite : 16 étapes distinctes tiennent encore dans
        // une requête, la 17e la fait déborder.
        val sixteen = TripRoutePlanner.planRequests(plan(ladder(16), returnToStart = true))
        val seventeen = TripRoutePlanner.planRequests(plan(ladder(17), returnToStart = true))

        assertEquals(1, sixteen.size)
        assertEquals(RouteApi.MAX_POINTS_PER_ROUTE_REQUEST, sixteen.single().size)
        assertEquals(0, sixteen.single().last())
        assertEquals(2, seventeen.size)
    }

    @Test
    fun onlyRecomputesTheLegsThatAreMissing() {
        val existing = listOf(leg(0, 1), leg(3, 4))
        val requests = TripRoutePlanner.planRequests(plan(ladder(5), legs = existing))

        assertEquals(listOf(listOf(1, 2, 3)), requests)
    }

    @Test
    fun groupsSeparateGapsIntoSeparateRequests() {
        val existing = listOf(leg(1, 2), leg(4, 5))
        val requests = TripRoutePlanner.planRequests(plan(ladder(7), legs = existing))

        assertEquals(listOf(listOf(0, 1), listOf(2, 3, 4), listOf(5, 6)), requests)
    }

    @Test
    fun asksForNothingWhenEveryLegIsKnown() {
        val complete = listOf(leg(0, 1), leg(1, 2))

        assertEquals(emptyList<List<Int>>(), TripRoutePlanner.planRequests(plan(ladder(3), legs = complete)))
    }

    @Test
    fun recomputesEverythingWhenForced() {
        val complete = listOf(leg(0, 1), leg(1, 2))

        assertEquals(
            listOf(listOf(0, 1, 2)),
            TripRoutePlanner.planRequests(plan(ladder(3), legs = complete), force = true)
        )
    }

    @Test
    fun asksForNothingBelowTwoSteps() {
        assertEquals(emptyList<List<Int>>(), TripRoutePlanner.planRequests(plan(ladder(1))))
        assertEquals(emptyList<List<Int>>(), TripRoutePlanner.planRequests(plan(emptyList())))
    }

    @Test
    fun countsTheClosingLegAsMissingOnALoop() {
        val openLegs = listOf(leg(0, 1), leg(1, 2))
        val requests = TripRoutePlanner.planRequests(plan(ladder(3), legs = openLegs, returnToStart = true))

        assertEquals(listOf(listOf(2, 0)), requests)
    }
}
