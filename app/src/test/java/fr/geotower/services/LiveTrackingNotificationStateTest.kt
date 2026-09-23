package fr.geotower.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LiveTrackingNotificationStateTest {

    @Test
    fun pauseKeepsTheLastNotificationData() {
        val last = LiveTrackingNotificationState(
            contentText = "Orange · 250 m",
            operator = "ORANGE",
            progress = 42,
            address = "Paris",
        )
        val fallback = LiveTrackingNotificationState(
            contentText = "Searching...",
            operator = "ORANGE",
        )

        assertSame(last, LiveTrackingNotificationState.forPause(last, fallback))
        assertEquals(fallback, LiveTrackingNotificationState.forPause(null, fallback))
    }
}
