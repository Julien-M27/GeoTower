package fr.geotower.data.workers

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class UpdateCheckSchedulerTest {
    private val paris = TimeZone.getTimeZone("Europe/Paris")

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun regularDaysRunEveryTwelveHoursAt08And20() {
        // Mercredi 25 novembre 2026
        val beforeMorning = millisAt(2026, Calendar.NOVEMBER, 25, 7, 30)
        assertEquals(TimeUnit.MINUTES.toMillis(30), UpdateCheckScheduler.delayUntilNextTargetMillis(beforeMorning, paris))

        val atMorning = millisAt(2026, Calendar.NOVEMBER, 25, 8, 0)
        assertEquals(TimeUnit.HOURS.toMillis(12), UpdateCheckScheduler.delayUntilNextTargetMillis(atMorning, paris))

        val atEvening = millisAt(2026, Calendar.NOVEMBER, 25, 20, 0)
        assertEquals(TimeUnit.HOURS.toMillis(12), UpdateCheckScheduler.delayUntilNextTargetMillis(atEvening, paris))
    }

    @Test
    fun thursdayRunsHourlyFrom15To24() {
        // Jeudi 26 novembre 2026
        val at08 = millisAt(2026, Calendar.NOVEMBER, 26, 8, 0)
        assertEquals(TimeUnit.HOURS.toMillis(7), UpdateCheckScheduler.delayUntilNextTargetMillis(at08, paris))

        val at1530 = millisAt(2026, Calendar.NOVEMBER, 26, 15, 30)
        assertEquals(TimeUnit.MINUTES.toMillis(30), UpdateCheckScheduler.delayUntilNextTargetMillis(at1530, paris))

        for (hour in 15..22) {
            val atHour = millisAt(2026, Calendar.NOVEMBER, 26, hour, 0)
            assertEquals(TimeUnit.HOURS.toMillis(1), UpdateCheckScheduler.delayUntilNextTargetMillis(atHour, paris))
        }

        // 23h -> minuit (24h, vendredi 00:00)
        val at23 = millisAt(2026, Calendar.NOVEMBER, 26, 23, 0)
        assertEquals(TimeUnit.HOURS.toMillis(1), UpdateCheckScheduler.delayUntilNextTargetMillis(at23, paris))

        // Vendredi 00:00 -> 08:00
        val fridayMidnight = millisAt(2026, Calendar.NOVEMBER, 27, 0, 0)
        assertEquals(TimeUnit.HOURS.toMillis(8), UpdateCheckScheduler.delayUntilNextTargetMillis(fridayMidnight, paris))
    }
}
