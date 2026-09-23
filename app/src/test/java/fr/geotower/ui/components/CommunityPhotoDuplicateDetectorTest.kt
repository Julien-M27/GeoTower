package fr.geotower.ui.components

import fr.geotower.data.community.CommunityDataPreferences
import fr.geotower.ui.screens.emitters.CommunityPhoto
import fr.geotower.ui.screens.emitters.CommunityPhotoDuplicateCache
import fr.geotower.ui.screens.emitters.communityPhotoInfoOperatorLabels
import fr.geotower.ui.screens.emitters.deduplicateCommunityPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityPhotoDuplicateDetectorTest {
    @Test
    fun deduplicateKeepsFirstPhotoAndListsOperatorsOfHiddenCopies() {
        val photos = listOf(
            photo(url = "first.jpg", operatorLabel = "Orange"),
            photo(url = "same-photo-from-sfr.jpg", operatorLabel = "SFR"),
            photo(url = "different.jpg", operatorLabel = "Bouygues Telecom")
        )

        val result = deduplicateCommunityPhotos(
            photos = photos,
            perceptualHashesByUrl = mapOf(
                "first.jpg" to 0L,
                "same-photo-from-sfr.jpg" to 3L,
                "different.jpg" to -1L
            )
        )

        assertEquals(listOf("first.jpg", "different.jpg"), result.map { it.url })
        assertEquals(listOf("SFR"), result.first().duplicateOperatorLabels)
    }

    @Test
    fun deduplicateDoesNotMergeHashesBeyondVisualDistanceThreshold() {
        val photos = listOf(
            photo(url = "first.jpg", operatorLabel = "Orange"),
            photo(url = "different.jpg", operatorLabel = "SFR")
        )

        val result = deduplicateCommunityPhotos(
            photos = photos,
            perceptualHashesByUrl = mapOf("first.jpg" to 0L, "different.jpg" to -1L)
        )

        assertEquals(2, result.size)
        assertEquals(emptyList<String>(), result.first().duplicateOperatorLabels)
    }

    @Test
    fun deduplicateRemovesExactUrlCopiesEvenWhenOneImageCouldNotBeLoaded() {
        val photos = listOf(
            photo(url = "same.jpg", operatorLabel = "Orange"),
            photo(url = "same.jpg", operatorLabel = "SFR")
        )

        val result = deduplicateCommunityPhotos(photos, perceptualHashesByUrl = emptyMap())

        assertEquals(1, result.size)
        assertEquals(listOf("SFR"), result.first().duplicateOperatorLabels)
    }

    @Test
    fun photoInfoListsCurrentAndHiddenOperators() {
        val photo = photo(url = "same.jpg", operatorLabel = "Orange").copy(
            duplicateOperatorLabels = listOf("SFR", "Bouygues Telecom")
        )

        assertEquals(
            listOf("Orange", "SFR", "Bouygues Telecom"),
            communityPhotoInfoOperatorLabels(photo)
        )
    }

    @Test
    fun duplicateHashes_arePersistedAndRetrievedPerPhotoUrl() {
        val prefs = TestSharedPreferences()

        CommunityPhotoDuplicateCache.putHashes(
            prefs,
            mapOf("first.jpg" to 42L, "second.jpg" to -7L)
        )

        assertEquals(42L, CommunityPhotoDuplicateCache.getHash(prefs, "first.jpg"))
        assertEquals(-7L, CommunityPhotoDuplicateCache.getHash(prefs, "second.jpg"))
        assertNull(CommunityPhotoDuplicateCache.getHash(prefs, "not-cached.jpg"))
    }

    private fun photo(url: String, operatorLabel: String) = CommunityPhoto(
        url = url,
        communityName = "Signal Quest",
        sourceId = CommunityDataPreferences.SOURCE_SIGNALQUEST,
        operatorLabel = operatorLabel
    )
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
