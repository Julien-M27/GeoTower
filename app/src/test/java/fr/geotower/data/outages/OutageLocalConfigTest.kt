package fr.geotower.data.outages

import android.content.SharedPreferences
import fr.geotower.data.models.SiteHsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutageLocalConfigTest {

    private fun site(id: String, operateur: String) =
        SiteHsEntity(idAnfr = id, operateur = operateur, latitude = 48.85, longitude = 2.35)

    @Test
    fun recordGenerationPersistsCountAndBreakdownSortedByOperator() {
        val prefs = FakePrefs()
        val config = OutageLocalConfig(prefs)

        config.recordGeneration(
            atMillis = 1_700_000_000_000L,
            sites = listOf(
                site("1", "SFR"),
                site("2", "Orange"),
                site("3", "Orange"),
                site("4", "Free Mobile"),
                site("5", "Orange"),
            ),
        )

        assertEquals(1_700_000_000_000L, config.lastGeneratedAtMillis)
        assertEquals(5, config.lastGeneratedCount)
        // Le plus touché en premier : c'est l'ordre affiché dans la page « Source des pannes ».
        assertEquals(
            listOf("Orange" to 3, "SFR" to 1, "Free Mobile" to 1),
            config.lastBreakdown,
        )
    }

    @Test
    fun breakdownSurvivesOperatorNamesContainingSeparators() {
        val prefs = FakePrefs()
        val config = OutageLocalConfig(prefs)

        config.lastBreakdown = listOf("Bouygues\tTelecom" to 12, "Free\nMobile" to 3)

        // Séparateurs neutralisés à l'écriture → relecture sans ligne perdue ni compte décalé.
        assertEquals(listOf("Bouygues Telecom" to 12, "Free Mobile" to 3), config.lastBreakdown)
    }

    @Test
    fun freshInstallHasNoLastGenerationSummary() {
        val config = OutageLocalConfig(FakePrefs())

        assertEquals(0L, config.lastGeneratedAtMillis)
        assertEquals(-1, config.lastGeneratedCount)
        assertTrue(config.lastBreakdown.isEmpty())
    }

    @Test
    fun corruptedBreakdownLinesAreIgnored() {
        val prefs = FakePrefs(OutageLocalConfig.KEY_LAST_BREAKDOWN to "Orange\t42\nn'importe quoi\nSFR\tabc\n\n")
        val config = OutageLocalConfig(prefs)

        assertEquals(listOf("Orange" to 42), config.lastBreakdown)
        assertNull(config.lastBreakdown.firstOrNull { it.first == "SFR" })
    }
}

/** SharedPreferences en mémoire minimal (lecture + écriture) pour les tests. */
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

        override fun remove(key: String?): SharedPreferences.Editor = apply { key?.let { removals.add(it) } }

        override fun clear(): SharedPreferences.Editor = apply { values.clear() }

        override fun commit(): Boolean {
            removals.forEach(values::remove)
            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
