package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SiteDateFormatterTest {
    @Test
    fun formatsIsoDateWithCurrentLocale() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)

            assertEquals("07/05/2026", formatDateToFrench("2026-05-07T12:30:00"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun keepsBlankDashAndMalformedValuesStable() {
        assertEquals("-", formatDateToFrench(null))
        assertEquals("-", formatDateToFrench(""))
        assertEquals("-", formatDateToFrench("-"))
        assertEquals("not-a-date", formatDateToFrench("not-a-date"))
    }
}
