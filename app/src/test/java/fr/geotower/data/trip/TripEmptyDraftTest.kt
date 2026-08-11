package fr.geotower.data.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEmptyDraftTest {
    @Test
    fun aDraftWithoutAnyStepIsDiscardable() {
        assertTrue(plan(emptyList()).isEmptyDraft())
    }

    @Test
    fun aDraftWithOneStepIsKept() {
        assertFalse(plan(listOf(step(48.85, 2.35))).isEmptyDraft())
    }

    @Test
    fun anEmptyTripTheUserScheduledIsKept() {
        // Poser une date fait passer le trajet en « à venir » : c'est un choix, même sans étape.
        val scheduled = plan(emptyList()).copy(
            status = TripPlan.STATUS_PLANNED,
            plannedAtMillis = 1_000L
        )

        assertFalse(scheduled.isEmptyDraft())
    }

    @Test
    fun anEmptyTripThatWasArchivedOrFinishedIsKept() {
        assertFalse(plan(emptyList()).copy(status = TripPlan.STATUS_ARCHIVED).isEmptyDraft())
        assertFalse(plan(emptyList()).copy(status = TripPlan.STATUS_DONE).isEmptyDraft())
    }
}
