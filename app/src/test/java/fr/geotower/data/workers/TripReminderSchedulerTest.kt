package fr.geotower.data.workers

import fr.geotower.data.trip.TripPlan
import fr.geotower.data.trip.ladder
import fr.geotower.data.trip.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripReminderSchedulerTest {
    private val now = 1_000_000_000_000L
    private val hour = 60 * 60 * 1000L

    private fun scheduled(
        plannedInHours: Double,
        offsets: List<Int>,
        status: String = TripPlan.STATUS_PLANNED
    ) = TripReminderScheduler.pendingReminders(
        plan = plan(ladder(2)).copy(
            plannedAtMillis = now + (plannedInHours * hour).toLong(),
            reminderOffsetsMinutes = offsets,
            status = status
        ),
        nowMillis = now
    )

    @Test
    fun schedulesOneReminderPerOffset() {
        val reminders = scheduled(plannedInHours = 48.0, offsets = listOf(24 * 60, 180))

        assertEquals(listOf(24 * 60, 180), reminders.map { it.first })
        assertEquals(24 * hour, reminders[0].second)
        assertEquals(45 * hour, reminders[1].second)
    }

    @Test
    fun dropsRemindersWhoseMomentHasPassed() {
        // Tournée dans 2 h : le rappel de la veille est derrière nous, celui d'une heure avant non.
        val reminders = scheduled(plannedInHours = 2.0, offsets = listOf(24 * 60, 60))

        assertEquals(listOf(60), reminders.map { it.first })
        assertEquals(hour, reminders.single().second)
    }

    @Test
    fun dropsAReminderThatFallsExactlyNow() {
        // Délai nul : le poster tout de suite n'apporte rien et double la notification d'un rappel
        // plus proche.
        assertTrue(scheduled(plannedInHours = 1.0, offsets = listOf(60)).isEmpty())
    }

    @Test
    fun schedulesNothingWithoutADate() {
        val undated = plan(ladder(2)).copy(reminderOffsetsMinutes = listOf(60))

        assertTrue(TripReminderScheduler.pendingReminders(undated, now).isEmpty())
    }

    @Test
    fun schedulesNothingForArchivedOrFinishedTrips() {
        assertTrue(scheduled(24.0, listOf(60), TripPlan.STATUS_ARCHIVED).isEmpty())
        assertTrue(scheduled(24.0, listOf(60), TripPlan.STATUS_DONE).isEmpty())
    }

    @Test
    fun schedulesNothingWithoutOffsets() {
        assertTrue(scheduled(plannedInHours = 24.0, offsets = emptyList()).isEmpty())
    }

    @Test
    fun keepsWorkNamesDistinctPerTripAndOffset() {
        assertTrue(
            TripReminderScheduler.workName("a", 60) != TripReminderScheduler.workName("a", 1440)
        )
        assertTrue(
            TripReminderScheduler.workName("a", 60) != TripReminderScheduler.workName("b", 60)
        )
        assertTrue(TripReminderScheduler.tripTag("a") != TripReminderScheduler.tripTag("b"))
    }
}
