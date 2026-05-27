package fr.geotower.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetUpdateSchedulerTest {
    @Test
    fun clampsPeriodicFrequencyToUiMinimum() {
        assertEquals(30L, WidgetUpdateScheduler.normalizedFrequencyMinutes(0))
        assertEquals(30L, WidgetUpdateScheduler.normalizedFrequencyMinutes(14))
        assertEquals(30L, WidgetUpdateScheduler.normalizedFrequencyMinutes(29))
        assertEquals(30L, WidgetUpdateScheduler.normalizedFrequencyMinutes(30))
        assertEquals(45L, WidgetUpdateScheduler.normalizedFrequencyMinutes(45))
    }

    @Test
    fun cancelsPeriodicWorkOnlyWhenNoWidgetProviderHasInstances() {
        assertTrue(WidgetUpdateScheduler.shouldCancelPeriodicWork(listOf(0, 0, 0, 0)))

        assertFalse(WidgetUpdateScheduler.shouldCancelPeriodicWork(listOf(1, 0, 0, 0)))
        assertFalse(WidgetUpdateScheduler.shouldCancelPeriodicWork(listOf(0, 2, 0, 0)))
        assertFalse(WidgetUpdateScheduler.shouldCancelPeriodicWork(listOf(0, 0, 0, 3)))
    }
}
