package fr.geotower.data.models

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyDetailsCodecTest {
    @Test
    fun decodeReturnsPlainTextUnchanged() {
        val details = "LTE 800 : 791-801 MHz | En service | 2026-05-07 | Panneau : 120° (28m)"

        assertEquals(details, FrequencyDetailsCodec.decode(details))
    }

    @Test
    fun decodeInflatesCompressedDetails() {
        val details = """
            LTE 800 : 791-801 MHz, 832-842 MHz | En service | 2026-05-07 | Panneau : 120° (28m)
            NR 3500 : 3710-3800 MHz | Techniquement opérationnel | 2026-05-07 | Panneau 5G : 240° (30m)
        """.trimIndent()

        assertEquals(details, FrequencyDetailsCodec.decode("Z1:${compress(details)}"))
    }

    private fun compress(value: String): String {
        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output, Deflater(9)).use { deflater ->
            deflater.write(value.toByteArray(Charsets.UTF_8))
        }
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }
}
