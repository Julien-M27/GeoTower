package fr.geotower.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SafeClickTest {
    @Test
    fun sameKeyIsDebouncedUntilIntervalPasses() {
        var now = 1_000L
        val safeClick = SafeClick(
            debounceMillis = { 700L },
            nowProvider = { now }
        )
        var clickCount = 0

        safeClick("primary") { clickCount++ }
        safeClick("primary") { clickCount++ }
        now += 700L
        safeClick("primary") { clickCount++ }

        assertEquals(2, clickCount)
    }

    @Test
    fun differentKeysAreNotDebouncedAgainstEachOther() {
        val safeClick = SafeClick(
            debounceMillis = { 700L },
            nowProvider = { 1_000L }
        )
        var firstClickCount = 0
        var secondClickCount = 0

        safeClick("first") { firstClickCount++ }
        safeClick("second") { secondClickCount++ }

        assertEquals(1, firstClickCount)
        assertEquals(1, secondClickCount)
    }
}
