package fr.geotower.services

/** Transitions for the controls exposed by the live-tracking notification. */
internal object LiveTrackingSessionState {
    enum class State {
        ACTIVE,
        PAUSED,
        ENDED,
    }

    enum class Action {
        PAUSE,
        RESUME,
        END,
    }

    fun reduce(state: State, action: Action): State = when {
        state == State.ACTIVE && action == Action.PAUSE -> State.PAUSED
        state == State.PAUSED && action == Action.RESUME -> State.ACTIVE
        action == Action.END -> State.ENDED
        else -> state
    }
}
