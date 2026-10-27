package fr.geotower.ui.components

import fr.geotower.data.api.ApiServer
import fr.geotower.ui.screens.about.formatAboutDatabaseVersion
import fr.geotower.ui.screens.about.formatAnfrWeeklyDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiServersDatabaseVersionsCardTest {

    @Test
    fun apiServerDbVersionInfo_defaultState() {
        val info = ApiServerDbVersionInfo(
            server = ApiServer.PRIMARY,
            isLoading = true
        )
        assertEquals(ApiServer.PRIMARY, info.server)
        assertTrue(info.isLoading)
        assertFalse(info.unreachable)
    }

    @Test
    fun apiServerDbVersionInfo_withVersions() {
        val info = ApiServerDbVersionInfo(
            server = ApiServer.PRIMARY,
            mobileDbVersion = "20261027_1426",
            mobileSizeBytes = 170119168L,
            radioDbVersion = "20261006_2001",
            radioSizeBytes = 22188032L,
            enbDbVersion = "2026-10-26-f999fd",
            enbSizeBytes = 8011776L,
            enbRowCount = 114688,
            liveDbVersion = "20261027_1426",
            liveGeneratedAt = "2026-10-27T13:29:36.821551+00:00",
            liveStationCount = 139362,
            anfrWeeklyDate = "2026-10-22T16:10:11",
            anfrMonthlyVersion = "20260930-export-etalab-data.zip",
            arcepQuarterlyVersion = "2026-T2",
            unreachable = false,
            isLoading = false
        )
        assertEquals("20261027_1426", info.mobileDbVersion)
        assertEquals(170119168L, info.mobileSizeBytes)
        assertEquals("20261006_2001", info.radioDbVersion)
        assertEquals(22188032L, info.radioSizeBytes)
        assertEquals("2026-10-26-f999fd", info.enbDbVersion)
        assertEquals(8011776L, info.enbSizeBytes)
        assertEquals(114688, info.enbRowCount)
        assertEquals("20261027_1426", info.liveDbVersion)
        assertEquals(139362, info.liveStationCount)
        assertEquals("2026-10-22T16:10:11", info.anfrWeeklyDate)
        assertEquals("20260930-export-etalab-data.zip", info.anfrMonthlyVersion)
        assertEquals("2026-T2", info.arcepQuarterlyVersion)
    }

    @Test
    fun formatAboutDatabaseVersion_formatsTimestampsCorrectly() {
        assertEquals("27/10/2026\nà 14:26", formatAboutDatabaseVersion("20261027_1426", "à"))
        assertEquals("06/10/2026\nà 20:01", formatAboutDatabaseVersion("20261006_2001", "à"))
        assertEquals("26/10/2026", formatAboutDatabaseVersion("2026-10-26-f999fd", "à"))
        assertEquals("-", formatAboutDatabaseVersion(null, "à"))
        assertEquals("-", formatAboutDatabaseVersion("", "à"))
    }

    @Test
    fun formatAnfrWeeklyDate_formatsIsoDate() {
        assertEquals("22/10/2026", formatAnfrWeeklyDate("2026-10-22T16:10:11"))
        assertEquals("22/10/2026", formatAnfrWeeklyDate("2026-10-22"))
        assertEquals("-", formatAnfrWeeklyDate(null))
        assertEquals("-", formatAnfrWeeklyDate("-"))
    }
}
