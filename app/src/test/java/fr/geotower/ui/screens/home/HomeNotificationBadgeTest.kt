package fr.geotower.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeNotificationBadgeTest {
    @Test
    fun keepsAllDigitsForTwoDigitCounts() {
        assertEquals("15", notificationBadgeText(15))
    }

    @Test
    fun capsCountsAboveNinetyNineWithoutDroppingThePlusSign() {
        assertEquals("99+", notificationBadgeText(100))
    }
}
