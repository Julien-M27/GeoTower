package fr.geotower.ui.screens.about

import fr.geotower.utils.ChangelogPagePrefs
import fr.geotower.utils.PageScrollPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppChangelogRepositoryTest {

    @Test
    fun `milestones should contain extensive version history from 1_0_0 to 2_1_18`() {
        val milestones = AppChangelogRepository.milestones
        assertTrue("Changelog repository should have rich history", milestones.size >= 90)

        // Latest version check
        val latest = milestones.first()
        assertEquals("2.1.18", latest.versionName)
        assertTrue(latest.isCurrent)
        assertEquals(MilestoneBadge.CURRENT, latest.badge)
        assertFalse(latest.summary.isBlank())
        assertTrue(latest.itemResIds.isNotEmpty() || latest.directItems.isNotEmpty())

        // Initial version check
        val initial = milestones.last()
        assertEquals("1.0.0", initial.versionName)
        assertEquals(MilestoneBadge.FOUNDATION, initial.badge)
        assertFalse(initial.summary.isBlank())
        assertTrue(initial.directItems.isNotEmpty())
    }

    @Test
    fun `milestone versions should be unique`() {
        val milestones = AppChangelogRepository.milestones
        val names = milestones.map { it.versionName }
        val duplicates = names.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertTrue("Duplicate versions found: ${duplicates.keys}", duplicates.isEmpty())
    }

    @Test
    fun `all milestones should have valid metadata`() {
        val milestones = AppChangelogRepository.milestones
        for (milestone in milestones) {
            assertTrue("Version name empty for $milestone", milestone.versionName.isNotBlank())
            assertTrue("Release date empty for ${milestone.versionName}", milestone.releaseDate.isNotBlank())
            assertTrue("Summary empty for ${milestone.versionName}", milestone.summary.isNotBlank())
            val hasItems = milestone.itemResIds.isNotEmpty() || milestone.directItems.isNotEmpty()
            assertTrue("No changelog items for ${milestone.versionName}", hasItems)
        }
    }

    @Test
    fun `play store milestone should be present`() {
        val playStore = AppChangelogRepository.milestones.find { it.versionName == "2.0.0" }
        assertNotNull("2.0.0 milestone should exist", playStore)
        assertEquals(MilestoneBadge.PLAY_STORE, playStore?.badge)
        assertTrue(playStore?.isMajor == true)
    }

    @Test
    fun `changelog page customization should be registered and configured`() {
        assertTrue(
            "CHANGELOGS page should be registered in customizablePages",
            PageScrollPrefs.customizablePages.contains(PageScrollPrefs.CHANGELOGS)
        )
        assertFalse(
            "CHANGELOGS page should no longer be in otherPages",
            PageScrollPrefs.otherPages.contains(PageScrollPrefs.CHANGELOGS)
        )
        assertTrue("showHeaderStats should be enabled by default", ChangelogPagePrefs.showHeaderStats.defaultValue)
        assertFalse("expandAllByDefault should be disabled by default", ChangelogPagePrefs.expandAllByDefault.defaultValue)
        assertTrue("showDates should be enabled by default", ChangelogPagePrefs.showDates.defaultValue)
        assertTrue("showBadges should be enabled by default", ChangelogPagePrefs.showBadges.defaultValue)
    }
}
