package fr.geotower.utils

import com.google.gson.Gson
import fr.geotower.data.models.LocalisationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedScreenDataTest {

    private val gson = Gson()

    @Test
    fun cachedScreenDataSerializesAndDeserializesCorrectly() {
        val entity = LocalisationEntity(
            idAnfr = "123456",
            operateur = "Orange",
            latitude = 48.8566,
            longitude = 2.3522,
            azimuts = "0/120/240",
            codeInsee = "75056",
            azimutsFh = null
        )
        val data = CachedScreenData(
            zoom = 14.5,
            lat = 48.8566,
            lon = 2.3522,
            antennas = listOf(entity),
            radioMarkers = emptyList()
        )

        val json = gson.toJson(data)
        val restored = gson.fromJson(json, CachedScreenData::class.java)

        assertNotNull(restored)
        assertEquals(14.5, restored.zoom, 0.001)
        assertEquals(48.8566, restored.lat, 0.0001)
        assertEquals(2.3522, restored.lon, 0.0001)

        val validAntennas = restored.antennas?.filterIsInstance<LocalisationEntity>() ?: emptyList()
        assertEquals(1, validAntennas.size)
        assertEquals("123456", validAntennas.first().idAnfr)
        assertEquals("Orange", validAntennas.first().operateur)
    }

    @Test
    fun filterIsInstanceSafelyRejectsUntypedMaps() {
        // Simule un JSON désérialisé sans type générique où les éléments sont des LinkedTreeMap
        val fakeUntypedList: List<Any> = listOf(
            mapOf("idAnfr" to "123456", "latitude" to 48.8),
            "randomString"
        )

        val valid = fakeUntypedList.filterIsInstance<LocalisationEntity>()
        assertTrue(valid.isEmpty())
        assertTrue(fakeUntypedList.size != valid.size)
    }
}
