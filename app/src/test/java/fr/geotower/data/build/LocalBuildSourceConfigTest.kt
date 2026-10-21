package fr.geotower.data.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBuildSourceConfigTest {

    @Test
    fun defaultSourceConfigResolvesToAdminPresets() {
        val config = LocalBuildSourceConfig()

        assertEquals(OfficialSources.WEEKLY_PRESET_DATAGOUV_ZIP, config.weeklyPreset)
        assertEquals(OfficialSources.URL_WEEKLY_DATAGOUV_ZIP, config.effectiveWeeklyUrl())

        assertEquals(OfficialSources.MONTHLY_PRESET_DATAGOUV_MONTHLY, config.monthlyPreset)
        assertEquals(OfficialSources.URL_MONTHLY_DATAGOUV, config.effectiveMonthlyUrl())

        assertEquals(OfficialSources.QUARTERLY_PRESET_ARCEP_LAST, config.quarterlyPreset)
        assertEquals(OfficialSources.URL_ARCEP_LAST, config.effectiveQuarterlyUrl())

        assertFalse(config.isWeeklyCustom)
        assertFalse(config.isMonthlyCustom)
        assertFalse(config.isQuarterlyCustom)
    }

    @Test
    fun customSourceConfigResolvesCustomUrls() {
        val config = LocalBuildSourceConfig(
            weeklyPreset = OfficialSources.PRESET_CUSTOM,
            weeklyCustomUrl = "https://example.com/custom_weekly.csv",
            monthlyPreset = OfficialSources.PRESET_CUSTOM,
            monthlyCustomUrl = "https://example.com/custom_monthly.zip",
            quarterlyPreset = OfficialSources.PRESET_CUSTOM,
            quarterlyCustomUrl = "https://example.com/custom_arcep.csv",
        )

        assertEquals("https://example.com/custom_weekly.csv", config.effectiveWeeklyUrl())
        assertEquals("https://example.com/custom_monthly.zip", config.effectiveMonthlyUrl())
        assertEquals("https://example.com/custom_arcep.csv", config.effectiveQuarterlyUrl())

        assertTrue(config.isWeeklyCustom)
        assertTrue(config.isMonthlyCustom)
        assertTrue(config.isQuarterlyCustom)
    }

    @Test
    fun weeklyPresetsResolveCorrectUrls() {
        val anfrApi = LocalBuildSourceConfig(weeklyPreset = OfficialSources.WEEKLY_PRESET_ANFR_API)
        assertEquals(OfficialSources.URL_WEEKLY_ANFR_API, anfrApi.effectiveWeeklyUrl())

        val anfrFallback = LocalBuildSourceConfig(weeklyPreset = OfficialSources.WEEKLY_PRESET_ANFR_API_FALLBACK)
        assertEquals(OfficialSources.URL_WEEKLY_ANFR_API_FALLBACK, anfrFallback.effectiveWeeklyUrl())

        val anfrPage = LocalBuildSourceConfig(weeklyPreset = OfficialSources.WEEKLY_PRESET_ANFR_PAGE)
        assertEquals(OfficialSources.URL_WEEKLY_ANFR_PAGE, anfrPage.effectiveWeeklyUrl())
    }
}
