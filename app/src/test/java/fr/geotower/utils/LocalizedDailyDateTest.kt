package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LocalizedDailyDateTest {

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    @Test
    fun formatsDailyDateInFrenchFormat() {
        assertEquals("20/10/2026", LocalizedDateLabels.formatVersionDate(rawValue = "2026-10-20", languagePreference = "fr"))
        assertEquals("20/10/2026", LocalizedDateLabels.formatVersionDate(rawValue = "20/10/2026", languagePreference = "fr"))
        assertEquals("20/10/2026", LocalizedDateLabels.formatVersionDate(rawValue = "2026-10-20T14:30:00Z", languagePreference = "fr"))
        assertEquals("20/10/2026", LocalizedDateLabels.formatVersionDate(rawValue = "2026-10-20 00:00:00", languagePreference = "fr"))
    }

    @Test
    fun formatsShortDateWithoutTimeInFrench() {
        val millis = utcMillis(2026, 10, 20, 14, 30, 0)
        // formatShortDate ne doit pas contenir l'heure
        val formatted = LocalizedDateLabels.formatShortDate(millis = millis, languagePreference = "fr")
        assertEquals(true, formatted.contains("2026"))
        assertEquals(false, formatted.contains(":"))
    }
}
