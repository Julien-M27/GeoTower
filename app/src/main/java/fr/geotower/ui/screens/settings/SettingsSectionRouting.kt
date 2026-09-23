package fr.geotower.ui.screens.settings

/** Stable numeric section ids used by the settings layout and search index. */
internal object SettingsSectionIds {
    const val APPEARANCE = 0
    const val MAPPING = 1
    const val GENERAL = 2
    const val PAGES = 3
    const val TRACKING = 4
    const val SYSTEM = 5
    const val DATA = 6
    const val COUNT = 7

    fun forSearchEntry(entry: SettingsSearchEntryId): Int = when (entry) {
        SettingsSearchEntryId.LOW_POWER -> SYSTEM
        SettingsSearchEntryId.API_SERVER -> DATA
    }

    /**
     * Maps public/deep-link section names to their current visual section.
     * Keep the legacy names stable: notifications and old shortcuts already use them.
     */
    fun forDeepLink(section: String?): Int? = when (section) {
        "offline_maps" -> MAPPING
        "database", "db_mobile", "db_radio", "db_enb", "db_outages", "db_local_build" -> DATA
        else -> null
    }
}

internal enum class SettingsSearchEntryId {
    LOW_POWER,
    API_SERVER
}
