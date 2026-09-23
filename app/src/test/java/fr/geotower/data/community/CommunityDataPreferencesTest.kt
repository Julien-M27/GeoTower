package fr.geotower.data.community

import fr.geotower.utils.OperatorColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityDataPreferencesTest {
    @Test
    fun orderedOperators_usesRequestedBaseOrder() {
        val orderedKeys = CommunityDataPreferences.orderedOperators(null).map { it.key }

        assertEquals(
            listOf(
                OperatorColors.ORANGE_KEY,
                OperatorColors.BOUYGUES_KEY,
                OperatorColors.SFR_KEY,
                OperatorColors.FREE_KEY
            ),
            orderedKeys.take(4)
        )
    }

    @Test
    fun orderedOperators_pinsDefaultOperatorWithoutDuplicate() {
        val orderedKeys = CommunityDataPreferences.orderedOperators("Free Mobile").map { it.key }

        assertEquals(OperatorColors.FREE_KEY, orderedKeys.first())
        assertEquals(orderedKeys.distinct(), orderedKeys)
        assertEquals(
            listOf(
                OperatorColors.FREE_KEY,
                OperatorColors.ORANGE_KEY,
                OperatorColors.BOUYGUES_KEY,
                OperatorColors.SFR_KEY
            ),
            orderedKeys.take(4)
        )
    }

    @Test
    fun orderedOperators_canPinSupportedOverseasOperator() {
        val orderedKeys = CommunityDataPreferences.orderedOperators("SRR").map { it.key }

        assertEquals(OperatorColors.SRR_KEY, orderedKeys.first())
        assertEquals(OperatorColors.ORANGE_KEY, orderedKeys[1])
    }

    @Test
    fun sourceIdForCommunityName_mapsKnownPhotoSources() {
        assertEquals(
            CommunityDataPreferences.SOURCE_CELLULARFR,
            CommunityDataPreferences.sourceIdForCommunityName("CellularFR")
        )
        assertEquals(
            CommunityDataPreferences.SOURCE_SIGNALQUEST,
            CommunityDataPreferences.sourceIdForCommunityName("Signal Quest")
        )
    }

    @Test
    fun duplicatePhotoHiding_isEnabledByDefaultAndCanBeDisabled() {
        val prefs = TestSharedPreferences()

        assertTrue(CommunityDataPreferences.hideDuplicatePhotos(prefs))

        CommunityDataPreferences.setHideDuplicatePhotos(prefs, false)

        assertFalse(CommunityDataPreferences.hideDuplicatePhotos(prefs))
    }
}

private class TestSharedPreferences(
    private val values: MutableMap<String, Any?> = mutableMapOf()
) : android.content.SharedPreferences {
    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun edit(): android.content.SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : android.content.SharedPreferences.Editor {
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
