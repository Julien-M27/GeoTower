package fr.geotower.data.build

import fr.geotower.data.models.FrequencyDetailsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrequencyDetailsEncoderTest {

    @Test
    fun encodeThenDecodeRoundTripsCompressibleText() {
        val details = """
            LTE 800 : 791-801 MHz, 832-842 MHz | En service | 2026-05-07 | Panneau : 120° (28m)
            NR 3500 : 3710-3800 MHz | Techniquement opérationnel | 2026-05-07 | Panneau 5G : 240° (30m)
            UMTS 2100 : 2110-2170 MHz | En service | 2026-05-07 | Panneau : 120° (28m)
        """.trimIndent()

        val encoded = FrequencyDetailsEncoder.encode(details)
        // Texte repetitif : la compression doit gagner et produire le prefixe Z1:.
        assertTrue("attendu un blob compresse", encoded!!.startsWith("Z1:"))
        assertTrue("le blob doit etre plus court que le texte", encoded.length < details.length)
        assertEquals(details, FrequencyDetailsCodec.decode(encoded))
    }

    @Test
    fun encodeKeepsShortTextRaw() {
        val short = "LTE 800"
        val encoded = FrequencyDetailsEncoder.encode(short)
        // Trop court pour gagner a la compression : on garde le texte brut.
        assertEquals(short, encoded)
        assertFalse(encoded!!.startsWith("Z1:"))
        assertEquals(short, FrequencyDetailsCodec.decode(encoded))
    }

    @Test
    fun encodeReturnsNullForEmpty() {
        assertNull(FrequencyDetailsEncoder.encode(null))
        assertNull(FrequencyDetailsEncoder.encode(""))
    }
}
