package fr.geotower.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

const val DEFAULT_SAFE_CLICK_DEBOUNCE_MS = 700L

@Stable
class SafeClick internal constructor(
    private val debounceMillis: () -> Long,
    private val nowProvider: () -> Long = SystemClock::elapsedRealtime
) {
    private val lastClickTimes = mutableMapOf<Any, Long>()

    operator fun invoke(action: () -> Unit) {
        invoke(action.javaClass.name, action)
    }

    operator fun invoke(key: Any?, action: () -> Unit) {
        val safeKey = key ?: action.javaClass.name
        val now = nowProvider()
        val lastClickTime = lastClickTimes[safeKey]

        if (lastClickTime == null || now - lastClickTime >= debounceMillis()) {
            lastClickTimes[safeKey] = now
            action()
        }
    }
}

@Composable
fun rememberSafeClick(
    debounceMillis: Long = DEFAULT_SAFE_CLICK_DEBOUNCE_MS
): SafeClick {
    val currentDebounceMillis = rememberUpdatedState(debounceMillis.coerceAtLeast(0L))
    return remember { SafeClick(debounceMillis = { currentDebounceMillis.value }) }
}
