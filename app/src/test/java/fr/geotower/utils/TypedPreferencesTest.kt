package fr.geotower.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedPreferencesTest {
    @Test
    fun mapDisplayPrefsKeepLegacySpeedometerKeyAndDefault() {
        val prefs = FakeSharedPreferences()

        assertEquals("show_speedometer", MapDisplayPrefs.showSpeedometer.key)
        assertTrue(MapDisplayPrefs.showSpeedometer.read(prefs))

        prefs.edit().putBoolean(MapDisplayPrefs.showSpeedometer.key, false).apply()

        assertFalse(MapDisplayPrefs.showSpeedometer.read(prefs))
    }

    @Test
    fun sitePageOrderNormalizesLegacyOrderWithoutRenamingKey() {
        val prefs = FakeSharedPreferences(
            SitePagePrefs.ORDER to "operator,map,support_details,photos,nav"
        )

        assertEquals("page_site_order", SitePagePrefs.ORDER)
        assertEquals(
            listOf(
                "operator",
                "map",
                "support_details",
                "elevation_profile",
                "throughput_calculator",
                "open_map",
                "photos",
                "speedtest",
                "nav"
            ),
            SitePagePrefs.order(prefs)
        )
    }

    @Test
    fun supportPageOrderKeepsOpenMapBeforeNavigation() {
        val normalized = SupportPagePrefs.normalizeOrder(
            listOf("map", "details", "nav", "open_map", "share")
        )

        assertEquals("page_support_order", SupportPagePrefs.ORDER)
        assertEquals(listOf("map", "details", "open_map", "nav", "share"), normalized)
    }

    @Test
    fun shareSiteOrderMigratesLegacyHeightsAndAddsNewBlocks() {
        val normalized = SharePrefs.normalizeSiteOrder("map,heights,address,address")

        assertEquals("share_order", SharePrefs.SITE_ORDER)
        assertEquals(listOf("map", "elevation_profile", "address", "speedtest", "throughput"), normalized)
        assertFalse(normalized.contains("heights"))
    }

    @Test
    fun throughputBlockOrderFiltersUnknownsAndAppendsMissingDefaults() {
        val normalized = ThroughputPrefs.normalizeBlockOrder(
            listOf("bands", "unknown", "summary", "bands")
        )

        assertEquals("page_throughput_order", ThroughputPrefs.BLOCK_ORDER)
        assertEquals("throughput_band_5g_3500", ThroughputPrefs.bandVisiblePrefKey("5g_3500"))
        assertEquals(
            listOf("bands", "summary", "header", "cone", "controls", "assumptions"),
            normalized
        )
    }

    @Test
    fun homeAndWidgetPrefsKeepDefaultsWhenLegacyValuesAreAbsent() {
        val prefs = FakeSharedPreferences()

        assertEquals("startup_page", HomePrefs.STARTUP_PAGE)
        assertEquals("home", HomePrefs.startupPage(prefs))
        assertEquals(WidgetPrefs.DEFAULT_SYNC_MINUTES, WidgetPrefs.syncFrequencyMinutes(prefs))

        prefs.edit().putInt(WidgetPrefs.SYNC_FREQUENCY_MINUTES, 5).apply()

        assertEquals(WidgetPrefs.MIN_SYNC_MINUTES, WidgetPrefs.syncFrequencyMinutes(prefs))
    }
}

private class FakeSharedPreferences(
    vararg initialValues: Pair<String, Any?>
) : SharedPreferences {
    private val values = linkedMapOf<String, Any?>().apply {
        initialValues.forEach { (key, value) -> put(key, value) }
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? {
        return values[key] as? String ?: defValue
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        return (values[key] as? Set<String>)?.toMutableSet() ?: defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return values[key] as? Int ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return values[key] as? Long ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return values[key] as? Float ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defValue
    }

    override fun contains(key: String?): Boolean {
        return values.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
            key?.let { pending[it] = value }
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
            key?.let { pending[it] = values?.toSet() }
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
            key?.let { pending[it] = value }
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
            key?.let { pending[it] = value }
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
            key?.let { pending[it] = value }
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
            key?.let { pending[it] = value }
        }

        override fun remove(key: String?): SharedPreferences.Editor = apply {
            key?.let { removals.add(it) }
        }

        override fun clear(): SharedPreferences.Editor = apply {
            clearRequested = true
        }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
