package fr.geotower.services

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTrackingSessionStateTest {

    @Test
    fun activeTrackingCanBePausedAndResumed() {
        val paused = LiveTrackingSessionState.reduce(
            state = LiveTrackingSessionState.State.ACTIVE,
            action = LiveTrackingSessionState.Action.PAUSE,
        )

        assertEquals(LiveTrackingSessionState.State.PAUSED, paused)
        assertEquals(
            LiveTrackingSessionState.State.ACTIVE,
            LiveTrackingSessionState.reduce(
                state = paused,
                action = LiveTrackingSessionState.Action.RESUME,
            ),
        )
    }

    @Test
    fun endingFromEitherActiveOrPausedIsTerminal() {
        assertEquals(
            LiveTrackingSessionState.State.ENDED,
            LiveTrackingSessionState.reduce(
                state = LiveTrackingSessionState.State.ACTIVE,
                action = LiveTrackingSessionState.Action.END,
            ),
        )
        assertEquals(
            LiveTrackingSessionState.State.ENDED,
            LiveTrackingSessionState.reduce(
                state = LiveTrackingSessionState.State.PAUSED,
                action = LiveTrackingSessionState.Action.END,
            ),
        )
    }

    @Test
    fun invalidActionsKeepCurrentState() {
        assertEquals(
            LiveTrackingSessionState.State.ACTIVE,
            LiveTrackingSessionState.reduce(
                state = LiveTrackingSessionState.State.ACTIVE,
                action = LiveTrackingSessionState.Action.RESUME,
            ),
        )
        assertEquals(
            LiveTrackingSessionState.State.PAUSED,
            LiveTrackingSessionState.reduce(
                state = LiveTrackingSessionState.State.PAUSED,
                action = LiveTrackingSessionState.Action.PAUSE,
            ),
        )
        assertEquals(
            LiveTrackingSessionState.State.ENDED,
            LiveTrackingSessionState.reduce(
                state = LiveTrackingSessionState.State.ENDED,
                action = LiveTrackingSessionState.Action.RESUME,
            ),
        )
    }
}
