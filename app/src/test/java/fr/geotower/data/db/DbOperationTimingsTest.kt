package fr.geotower.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class DbOperationTimingsTest {

    @Test
    fun formatsSecondsOnlyUnderOneMinute() {
        assertEquals("0 s", DbOperationTimings.formatDuration(0L))
        assertEquals("45 s", DbOperationTimings.formatDuration(45_000L))
        assertEquals("59 s", DbOperationTimings.formatDuration(59_900L)) // tronque a la seconde
    }

    @Test
    fun formatsMinutesAndSecondsUnderOneHour() {
        assertEquals("1 min 00 s", DbOperationTimings.formatDuration(60_000L))
        assertEquals("4 min 32 s", DbOperationTimings.formatDuration(272_000L))
        assertEquals("59 min 59 s", DbOperationTimings.formatDuration(3_599_000L))
    }

    @Test
    fun formatsHoursAndMinutesWithoutSeconds() {
        assertEquals("1 h 00 min", DbOperationTimings.formatDuration(3_600_000L))
        assertEquals("1 h 03 min", DbOperationTimings.formatDuration(3_780_000L))
        assertEquals("2 h 15 min", DbOperationTimings.formatDuration(8_100_000L))
    }

    @Test
    fun clampsNegativeDurationToZero() {
        assertEquals("0 s", DbOperationTimings.formatDuration(-5_000L))
    }
}
