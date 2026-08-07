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

    /**
     * Non-regression API 26 : l'encodeur utilisait `java.util.Base64`, indisponible sous Android
     * 8.0 alors que le `minSdk` de l'app est 24 — la generation locale plantait en
     * `NoClassDefFoundError` sur Android 7.0/7.1, precisement le parc d'appareils modestes qu'on
     * cherche a servir. Il encode desormais lui-meme ; sa sortie doit rester **strictement** celle
     * du JDK, alphabet et remplissage compris, pour toutes les longueurs (les trois cas de
     * padding). Le blob est relu par la vraie fonction de decodage de l'app.
     */
    @Test
    fun base64OutputIsIdenticalToTheJdkForEveryPaddingLength() {
        for (length in 1..120) {
            val details = "Systemes: FM x$length\n" + "Frequences: 87.5-108 MHz\n".repeat(3) + "x".repeat(length)
            val encoded = FrequencyDetailsEncoder.encode(details)!!
            assertTrue("attendu un blob compresse pour $length", encoded.startsWith("Z1:"))

            val payload = encoded.substring(3)
            val bytes = java.util.Base64.getDecoder().decode(payload)
            assertEquals(
                "encodage divergent du JDK pour $length octets",
                java.util.Base64.getEncoder().encodeToString(bytes),
                payload,
            )
            assertEquals(details, FrequencyDetailsCodec.decode(encoded))
        }
    }

    /**
     * Non-regression du leak `Deflater` : chaque appel a `encode` allouait un `Deflater` (memoire
     * NATIVE zlib) jamais libere. Sur des centaines de milliers d'appels (emission on-device des
     * ~200k+ blobs radio, apres le build mobile), la generation locale finissait en
     * `OutOfMemoryError` (Deflater.init natif). Cette boucle doit passer sans OOM.
     */
    @Test
    fun manyEncodesDoNotExhaustNativeMemory() {
        repeat(300_000) { i ->
            val text = "Systemes: FM x2\nFrequences: $i-${i + 1} MHz\nAntennes: Panneau broadcast: 90 deg (20m)"
            assertTrue(FrequencyDetailsEncoder.encode(text) != null)
        }
    }
}
