package fr.geotower.ui.components

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteExternalLinksTest {

    @Test
    fun siteExternalLinkDefinitions_placesSignalQuestBeforeCellularFr() {
        val signalQuestIndex = SiteExternalLinkDefinitions.indexOfFirst { it.id == "signalquest" }
        val cellularFrIndex = SiteExternalLinkDefinitions.indexOfFirst { it.id == "cellularfr" }

        assertTrue("signalquest doit être présent", signalQuestIndex >= 0)
        assertTrue("cellularfr doit être présent", cellularFrIndex >= 0)
        assertTrue("signalquest doit être placé avant cellularfr", signalQuestIndex < cellularFrIndex)
    }

    @Test
    fun defaultSiteExternalLinkOrder_placesSignalQuestBeforeCellularFrWhenPresent() {
        val order = defaultSiteExternalLinkOrder()
        val signalQuestIndex = order.indexOf("signalquest")
        assertTrue("signalquest doit être présent dans l'ordre par défaut", signalQuestIndex >= 0)

        val cellularFrIndex = order.indexOf("cellularfr")
        if (cellularFrIndex >= 0) {
            assertTrue("signalquest doit précéder cellularfr", signalQuestIndex < cellularFrIndex)
        }
    }

    @Test
    fun readSiteExternalLinkOrder_fallsBackToDefaultOrder() {
        val prefs = InMemorySharedPreferences()
        val order = readSiteExternalLinkOrder(prefs)

        val signalQuestIndex = order.indexOf("signalquest")
        assertTrue(signalQuestIndex >= 0)
        assertEquals(defaultSiteExternalLinkOrder(), order)
    }

    @Test
    fun resetSiteExternalLinks_restoresDefaultOrder() {
        val prefs = InMemorySharedPreferences()
        writeSiteExternalLinkOrder(prefs, listOf("cellmapper", "signalquest"))

        resetSiteExternalLinks(prefs)

        val restoredOrder = readSiteExternalLinkOrder(prefs)
        assertEquals(defaultSiteExternalLinkOrder(), restoredOrder)
    }
}

private class InMemorySharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?) = apply { if (key != null) values[key] = value }
        override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { if (key != null) values[key] = value?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { if (key != null) values[key] = value }
        override fun putLong(key: String?, value: Long) = apply { if (key != null) values[key] = value }
        override fun putFloat(key: String?, value: Float) = apply { if (key != null) values[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) values[key] = value }
        override fun remove(key: String?) = apply { if (key != null) values.remove(key) }
        override fun clear() = apply { values.clear() }
        override fun commit(): Boolean = true
        override fun apply() = Unit
    }
}
