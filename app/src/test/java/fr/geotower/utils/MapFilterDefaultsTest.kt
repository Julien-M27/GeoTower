package fr.geotower.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapFilterDefaultsTest {

    @Test
    fun referenceOnEmptyPrefsReturnsFactoryDefaults() {
        val ref = MapFilterDefaults.reference(FakePrefs())

        // Tant que rien n'est configuré, la référence = l'usine → bandeau inchangé.
        assertEquals(OperatorColors.defaultVisibleKeys, ref.operatorKeys)
        assertTrue(ref.frequency.isFullyEnabled)
        assertTrue(ref.showSitesInService)
        assertTrue(ref.showSitesOutOfService)
        assertFalse(ref.hideUndergroundSites)
        assertFalse(ref.showOnlyZbSites)
        assertFalse(ref.showRadioTv)
        assertFalse(ref.showRadioBroadcast)
        assertFalse(ref.showRadioPrivateMobile)
        assertFalse(ref.showRadioFh)
        assertFalse(ref.showRadioOther)
        assertFalse(ref.showSignalQuestCoveragePoints)
        assertEquals(AppConfig.defaultSignalQuestCoverageOperatorKeys, ref.signalQuestCoverageOperatorKeys)
    }

    @Test
    fun referenceReadsStoredBooleanSnapshot() {
        val prefs = FakePrefs(
            "filter_default_show_sites_out_of_service" to false,
            "filter_default_hide_underground_sites" to true,
            "filter_default_show_techno_2g" to false,
            "filter_default_f5g_3500" to false,
            "filter_default_show_radio_tv" to true
        )

        val ref = MapFilterDefaults.reference(prefs)

        assertFalse(ref.showSitesOutOfService)
        assertTrue(ref.hideUndergroundSites)
        assertFalse(ref.frequency.show2G)
        assertFalse(ref.frequency.f5G3500)
        assertTrue(ref.showRadioTv)
        // Une techno / une bande désactivée dans le défaut ⇒ la référence n'est plus "pleine".
        assertFalse(ref.frequency.isFullyEnabled)
        // Les clés non stockées retombent bien sur l'usine.
        assertTrue(ref.showSitesInService)
        assertTrue(ref.frequency.show5G)
        assertEquals(OperatorColors.defaultVisibleKeys, ref.operatorKeys)
    }

    @Test
    fun referenceReadsStoredOperatorSnapshot() {
        val prefs = FakePrefs(
            "filter_default_selected_operator_keys" to setOf(OperatorColors.ORANGE_KEY)
        )

        val ref = MapFilterDefaults.reference(prefs)

        assertEquals(setOf(OperatorColors.ORANGE_KEY), ref.operatorKeys)
    }
}

/** SharedPreferences en mémoire minimal pour les tests (lecture seule ici). */
private class FakePrefs(
    vararg initialValues: Pair<String, Any?>
) : SharedPreferences {
    private val values = linkedMapOf<String, Any?>().apply {
        initialValues.forEach { (key, value) -> put(key, value) }
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearRequested = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply { key?.let { pending[it] = values?.toSet() } }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply { key?.let { pending[it] = value } }
        override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { removals.add(it) } }
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

        override fun commit(): Boolean {
            if (clearRequested) values.clear()
            removals.forEach(values::remove)
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }

        override fun apply() { commit() }
    }
}
