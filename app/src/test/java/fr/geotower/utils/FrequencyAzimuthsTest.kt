package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrequencyAzimuthsTest {
    @Test
    fun parsesPipeSeparatedAzimuthsAndNormalizesValues() {
        assertEquals(listOf(0, 120, 240), FrequencyAzimuths.parseList("0|120|240"))
        assertEquals(listOf(90), FrequencyAzimuths.parseList("90"))
        assertEquals(listOf(0, 120), FrequencyAzimuths.parseList("360|120|120|oops|-1|361|"))
        assertEquals(emptyList<Int>(), FrequencyAzimuths.parseList(null))
        assertEquals(emptyList<Int>(), FrequencyAzimuths.parseList("  |abc||"))
    }

    @Test
    fun encodesAndDecodesNormalizedDeterministicMapping() {
        val encoded = FrequencyAzimuths.encode(
            mapOf("  lte 800 " to setOf(240, 0, 360), "LTE 800" to setOf(120), "  " to setOf(60)),
        )

        assertEquals("{\"LTE 800\":[0,120,240]}", encoded)
        assertEquals(mapOf("LTE 800" to setOf(0, 120, 240)), FrequencyAzimuths.decode(encoded))
        assertEquals(emptyMap<String, Set<Int>>(), FrequencyAzimuths.decode(null))
        assertEquals(emptyMap<String, Set<Int>>(), FrequencyAzimuths.decode("not json"))
        assertNull(FrequencyAzimuths.encode(mapOf("LTE 800" to emptySet())))
    }
}
