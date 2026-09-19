package fr.geotower.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class SiteHideLongPressTest {
    @Test
    fun siteHidingWaitsOneSecondBeforeTriggering() {
        assertEquals(1_000L, siteHideLongPressTimeoutMillis())
    }
}
