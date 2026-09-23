package fr.geotower.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSectionRoutingTest {
    @Test
    fun batteryAndApiServerSearchEntriesUseTheirNewSections() {
        assertEquals(
            SettingsSectionIds.SYSTEM,
            SettingsSectionIds.forSearchEntry(SettingsSearchEntryId.LOW_POWER)
        )
        assertEquals(
            SettingsSectionIds.DATA,
            SettingsSectionIds.forSearchEntry(SettingsSearchEntryId.API_SERVER)
        )
    }

    @Test
    fun legacySettingsDeepLinksKeepTheirTargetsAfterRenamingSections() {
        assertEquals(SettingsSectionIds.DATA, SettingsSectionIds.forDeepLink("database"))
        assertEquals(SettingsSectionIds.DATA, SettingsSectionIds.forDeepLink("db_mobile"))
        assertEquals(SettingsSectionIds.DATA, SettingsSectionIds.forDeepLink("db_local_build"))
        assertEquals(SettingsSectionIds.MAPPING, SettingsSectionIds.forDeepLink("offline_maps"))
        assertNull(SettingsSectionIds.forDeepLink("unknown"))
    }
}
