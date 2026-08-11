package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSchedulingRulesTest {
    @Test
    fun datingADraftPutsItInTheUpcomingFilter() {
        val draft = plan(ladder(2))

        assertEquals(TripPlan.STATUS_PLANNED, draft.statusAfterScheduling(1_000L))
    }

    @Test
    fun removingTheDateSendsItBackToDrafts() {
        val planned = plan(ladder(2)).copy(status = TripPlan.STATUS_PLANNED, plannedAtMillis = 1_000L)

        assertEquals(TripPlan.STATUS_DRAFT, planned.statusAfterScheduling(null))
    }

    @Test
    fun datingAFinishedOrArchivedTripDoesNotBringItBack() {
        val done = plan(ladder(2)).copy(status = TripPlan.STATUS_DONE)
        val archived = plan(ladder(2)).copy(status = TripPlan.STATUS_ARCHIVED)

        assertEquals(TripPlan.STATUS_DONE, done.statusAfterScheduling(1_000L))
        assertEquals(TripPlan.STATUS_ARCHIVED, archived.statusAfterScheduling(1_000L))
    }

    @Test
    fun aNewTripCarriesAnAutomaticName() {
        assertTrue(TripPlanStore.newPlan(name = "Tournée du 12 août 2026").autoNamed)
    }

    @Test
    fun aCopyKeepsItsOwnName() {
        val original = TripPlanStore.newPlan(name = "Tournée du 12 août 2026")

        val copy = TripPlanStore.duplicated(original, name = "Tournée du 12 août 2026 (copie)")

        // « … (copie) » est un nom porté par la copie : le dater ne doit pas le réécrire.
        assertFalse(copy.autoNamed)
    }

    @Test
    fun anOlderTripIsTreatedAsManuallyNamed() {
        // Gson relit un booléen absent à `false` : un trajet écrit avant ce champ garde son titre.
        assertFalse(plan(ladder(2)).autoNamed)
    }
}
